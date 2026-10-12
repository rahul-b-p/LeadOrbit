package com.leadorbit.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.leadorbit.catalog.domain.TaxClass;

/**
 * GST calculation engine: tax-class aware line computation with CGST/SGST (intra-state)
 * vs IGST (inter-state) splitting, tax-inclusive reversement, and invoice-level rounding.
 *
 * <p>Rates and HSN/SAC applicability must be validated by a tax professional; the engine
 * faithfully implements the documented algorithm from PHASE-2-DESIGN §4.2.</p>
 */
@Service
public class GstCalculationService {

    public LineBreakdown computeLine(LineInput input) {
        BigDecimal gross = input.quantity().multiply(input.rate());
        BigDecimal discount = input.discount() == null ? BigDecimal.ZERO : input.discount();
        BigDecimal net = gross.subtract(discount);

        boolean noTax = input.taxClass() == TaxClass.EXEMPT
            || input.taxClass() == TaxClass.NIL_RATED
            || input.taxClass() == TaxClass.NON_GST
            || input.gstRate().signum() == 0;

        BigDecimal taxable;
        if (noTax) {
            taxable = net;
        } else if (input.taxInclusive()) {
            // Extract the tax portion from an inclusive price.
            BigDecimal divisor = BigDecimal.valueOf(100).add(input.gstRate());
            taxable = net.multiply(BigDecimal.valueOf(100)).divide(divisor, 2, RoundingMode.HALF_UP);
        } else {
            taxable = net;
        }
        taxable = taxable.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = noTax
            ? BigDecimal.ZERO
            : taxable.multiply(input.gstRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        if (tax.signum() > 0) {
            boolean intraState = Objects.equals(input.sellerState(), input.placeOfSupplyState());
            if (intraState) {
                cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sgst = tax.subtract(cgst); // absorb rounding residue in SGST
            } else {
                igst = tax;
            }
        }

        BigDecimal lineTotal = taxable.add(cgst).add(sgst).add(igst);
        return new LineBreakdown(taxable, cgst, sgst, igst, BigDecimal.ZERO, lineTotal);
    }

    public InvoiceTotals totalize(List<LineBreakdown> lines, RoundingPolicy policy) {
        BigDecimal taxable = sum(lines, LineBreakdown::taxableValue);
        BigDecimal cgst = sum(lines, LineBreakdown::cgst);
        BigDecimal sgst = sum(lines, LineBreakdown::sgst);
        BigDecimal igst = sum(lines, LineBreakdown::igst);
        BigDecimal cess = sum(lines, LineBreakdown::cess);

        BigDecimal unrounded = taxable.add(cgst).add(sgst).add(igst).add(cess);
        BigDecimal roundOff = BigDecimal.ZERO;
        BigDecimal grandTotal = unrounded;

        if (policy == RoundingPolicy.NEAREST_RUPEE) {
            grandTotal = unrounded.setScale(0, RoundingMode.HALF_UP);
            roundOff = grandTotal.subtract(unrounded);
        }
        return new InvoiceTotals(taxable, cgst, sgst, igst, cess, grandTotal, roundOff);
    }

    private BigDecimal sum(List<LineBreakdown> lines,
                           java.util.function.Function<LineBreakdown, BigDecimal> extractor) {
        return lines.stream().map(extractor)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
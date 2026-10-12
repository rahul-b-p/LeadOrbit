package com.leadorbit.billing.pdf;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Renders an issued invoice to a GST-compliant PDF (seller/customer blocks, line table with
 * CGST/SGST/IGST breakup, totals). Pure function over {@link InvoicePdfData}; no I/O except
 * the returned bytes.
 */
public final class InvoicePdfGenerator {

    private InvoicePdfGenerator() {
    }

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    public static byte[] generate(InvoicePdfData data) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("TAX INVOICE", TITLE));
            document.add(new Paragraph(data.sellerLegalName(), HEADER));
            if (data.sellerTradeName() != null && !data.sellerTradeName().isBlank()) {
                document.add(new Paragraph(data.sellerTradeName(), SMALL));
            }
            document.add(new Paragraph("GSTIN: " + data.sellerGstin(), HEADER));
            document.add(new Paragraph(data.sellerAddress(), SMALL));
            document.add(new Paragraph("State Code: " + data.sellerStateCode(), SMALL));

            document.add(new Paragraph(" "));
            document.add(metaTable(data));

            document.add(new Paragraph(" "));
            document.add(linesTable(data));

            document.add(new Paragraph(" "));
            document.add(totalsTable(data));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render invoice PDF", ex);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static PdfPTable metaTable(InvoicePdfData data) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        cell(table, "Invoice Number: " + data.invoiceNumber(), true);
        cell(table, "Invoice Date: " + data.invoiceDate(), false);
        cell(table, "Due Date: " + (data.dueDate() == null ? "-" : data.dueDate()), true);
        cell(table, "Place of Supply: " + data.placeOfSupplyState(), false);
        cell(table, "Status: " + data.status(), true);
        table.completeRow();
        return table;
    }

    private static PdfPTable linesTable(InvoicePdfData data) {
        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {4.2f, 1.4f, 0.8f, 0.8f, 1.3f, 1.3f, 1.1f, 1.1f, 1.5f});
        String[] headings = {"Description", "HSN/SAC", "Qty", "Unit", "Rate", "Taxable",
            "CGST", "SGST", "Line Total"};
        for (String heading : headings) {
            PdfPCell h = new PdfPCell(new Phrase(heading, BOLD));
            h.setBackgroundColor(new java.awt.Color(235, 235, 235));
            h.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(h);
        }
        for (InvoicePdfData.Line line : data.lines()) {
            cell(table, line.description(), false);
            cell(table, line.hsnSac(), false);
            cell(table, num(line.quantity()), false);
            cell(table, line.unit(), false);
            cell(table, num(line.rate()), false);
            cell(table, num(line.taxableValue()), false);
            cell(table, num(line.cgstAmount()), false);
            cell(table, num(line.sgstAmount()), false);
            cell(table, money(line.lineTotal()), true);
        }
        table.completeRow();
        return table;
    }

    private static PdfPTable totalsTable(InvoicePdfData data) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        row(table, "Taxable Amount", money(data.taxableAmount()));
        row(table, "CGST", money(data.cgstAmount()));
        row(table, "SGST", money(data.sgstAmount()));
        row(table, "IGST", money(data.igstAmount()));
        row(table, "Cess", money(data.cessAmount()));
        row(table, "Round Off", money(data.roundOff()));
        row(table, "Grand Total", money(data.grandTotal()));
        row(table, "Amount Paid", money(data.amountPaid()));
        row(table, "Balance", money(data.grandTotal().subtract(data.amountPaid()).max(BigDecimal.ZERO)));
        return table;
    }

    private static void row(PdfPTable table, String label, String value) {
        cell(table, label, false);
        cell(table, value, true);
    }

    private static void cell(PdfPTable table, String text, boolean right) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, SMALL));
        cell.setBorder(Rectangle.NO_BORDER);
        if (right) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        table.addCell(cell);
    }

    private static String num(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return String.format(Locale.ROOT, "INR %.2f", v);
    }
}
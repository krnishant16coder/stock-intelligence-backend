package com.stockintelligence.notification;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.ReportService;
import com.stockintelligence.report.StockAnalysisResponse;
import java.util.List;

/**
 * Human-readable mail bodies for digest, alert and report mails.
 * Every mail is built as plain text + HTML (multipart/alternative).
 * HTML uses tables and inline styles only so Gmail and other clients render it.
 */
public final class MailTemplate {

    private MailTemplate() {}

    public record MailBodies(String plain, String html) {}

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** DOWNTREND -> Downtrend, BUY_MORE -> Buy more, UNKNOWN -> Unknown. */
    static String word(String token) {
        if (token == null || token.isBlank()) {
            return "Unknown";
        }
        String[] parts = token.strip().toLowerCase(java.util.Locale.ROOT).split("[_\\- ]+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return out.isEmpty() ? "Unknown" : out.toString();
    }

    private static String badge(String text, String background) {
        return "<span style=\"display:inline-block;background:" + background
                + ";color:#ffffff;font-size:12px;font-weight:bold;border-radius:10px;"
                + "padding:2px 10px;margin-right:6px;\">" + escape(text) + "</span>";
    }

    static String signalBadge(String signal) {
        String bg = switch (signal == null ? "" : signal) {
            case "BUY_MORE" -> "#1a7f37";
            case "HOLD" -> "#57606a";
            case "REVIEW" -> "#9a6700";
            case "HIGH_RISK" -> "#b42318";
            default -> "#57606a";
        };
        return badge(word(signal), bg);
    }

    static String riskBadge(String risk) {
        String bg = switch (risk == null ? "" : risk) {
            case "LOW" -> "#1a7f37";
            case "MEDIUM" -> "#9a6700";
            case "HIGH" -> "#b42318";
            case "CRITICAL" -> "#7a0c0c";
            default -> "#57606a";
        };
        return badge(word(risk) + " risk", bg);
    }

    static String reportUrl(String baseUrl, long reportId) {
        String path = "/api/reports/" + reportId;
        if (baseUrl == null || baseUrl.isBlank()) {
            return path;
        }
        String base = baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static String stockCardHtml(String symbol, String company, String signal, String risk,
                                        String priceTrend, String fundamentalTrend, String newsImpact,
                                        boolean criticalAlert, String summary, List<String> keyReasons) {
        StringBuilder h = new StringBuilder();
        h.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:12px 0;\"><tr><td ")
                .append("style=\"border:1px solid #d0d7de;border-radius:8px;padding:12px;\">");
        h.append("<div style=\"font-size:15px;margin-bottom:6px;\"><b>")
                .append(escape(symbol)).append("</b> <span style=\"color:#57606a;\">")
                .append(escape(company)).append("</span></div>");
        h.append("<div style=\"margin-bottom:8px;\">").append(signalBadge(signal))
                .append(riskBadge(risk));
        if (criticalAlert) {
            h.append(badge("Critical alert", "#7a0c0c"));
        }
        h.append("</div>");
        h.append("<div style=\"font-size:13px;color:#57606a;margin-bottom:8px;\">Price: ")
                .append(escape(word(priceTrend))).append(" &middot; Fundamentals: ")
                .append(escape(word(fundamentalTrend))).append(" &middot; News: ")
                .append(escape(word(newsImpact))).append("</div>");
        if (summary != null && !summary.isBlank()) {
            h.append("<div style=\"font-size:14px;line-height:1.5;margin-bottom:8px;\">")
                    .append(escape(summary)).append("</div>");
        }
        if (keyReasons != null && !keyReasons.isEmpty()) {
            h.append("<div style=\"font-size:13px;font-weight:bold;margin-bottom:4px;\">Why this view:</div><ul ")
                    .append("style=\"font-size:13px;line-height:1.5;margin:0 0 4px 0;padding-left:20px;\">");
            for (String reason : keyReasons) {
                if (reason != null && !reason.isBlank()) {
                    h.append("<li>").append(escape(reason)).append("</li>");
                }
            }
            h.append("</ul>");
        }
        h.append("</td></tr></table>");
        return h.toString();
    }

    private static String stockCardPlain(String symbol, String company, String signal, String risk,
                                         String priceTrend, String fundamentalTrend, String newsImpact,
                                         boolean criticalAlert, String summary, List<String> keyReasons) {
        StringBuilder p = new StringBuilder();
        p.append(symbol).append(" — ").append(company).append("\n")
                .append("Signal: ").append(word(signal)).append(" | Risk: ").append(word(risk));
        if (criticalAlert) {
            p.append(" | CRITICAL ALERT");
        }
        p.append("\nPrice trend: ").append(word(priceTrend))
                .append(" | Fundamentals: ").append(word(fundamentalTrend))
                .append(" | News: ").append(word(newsImpact)).append("\n");
        if (summary != null && !summary.isBlank()) {
            p.append(summary.strip()).append("\n");
        }
        if (keyReasons != null && !keyReasons.isEmpty()) {
            p.append("Why this view:\n");
            for (String reason : keyReasons) {
                if (reason != null && !reason.isBlank()) {
                    p.append("  - ").append(reason.strip()).append("\n");
                }
            }
        }
        return p.toString();
    }

    private static String pageHtml(String titleLine, String bodyInner) {
        return "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222222;"
                + "max-width:640px;margin:0 auto;padding:16px;\">"
                + "<h2 style=\"font-size:18px;margin:0 0 4px 0;\">Stock Intelligence</h2>"
                + "<p style=\"font-size:14px;color:#57606a;margin:0 0 16px 0;\">" + titleLine + "</p>"
                + bodyInner
                + "<p style=\"font-size:12px;color:#888888;margin-top:20px;\">"
                + "Automated analysis for your watchlists — not financial advice.</p>"
                + "</body></html>";
    }

    // ---- Daily digest ----

    public static MailBodies digest(String dateLabel, List<ReportResponse> reports, String baseUrl) {
        int stocks = reports.stream().mapToInt(ReportResponse::stocksAnalyzed).sum();
        String title = "Market-close digest for " + dateLabel + " — "
                + reports.size() + " watchlists, " + stocks + " stocks";

        StringBuilder plain = new StringBuilder(title).append("\n\n");
        StringBuilder inner = new StringBuilder();
        for (ReportResponse report : reports) {
            plain.append("## ").append(report.watchlistName())
                    .append(" (report #").append(report.id()).append(")\n")
                    .append(report.summary()).append("\n\n");
            inner.append("<h3 style=\"font-size:16px;margin:20px 0 4px 0;\">")
                    .append(escape(report.watchlistName()))
                    .append(" <span style=\"font-size:12px;font-weight:normal;color:#57606a;\">Report #")
                    .append(report.id()).append("</span></h3>");
            inner.append("<p style=\"font-size:13px;color:#57606a;margin:0 0 8px 0;\">")
                    .append(escape(report.summary())).append("</p>");
            for (StockAnalysisResponse a : report.analyses()) {
                plain.append(stockCardPlain(a.symbol(), a.companyName(), a.signal(), a.riskLevel(),
                        a.priceTrend(), a.fundamentalTrend(), a.newsImpact(),
                        a.criticalAlert(), a.summary(), a.keyReasons())).append("\n");
                inner.append(stockCardHtml(a.symbol(), a.companyName(), a.signal(), a.riskLevel(),
                        a.priceTrend(), a.fundamentalTrend(), a.newsImpact(),
                        a.criticalAlert(), a.summary(), a.keyReasons()));
            }
            String url = reportUrl(baseUrl, report.id());
            plain.append("Full report: ").append(url).append("\n\n");
            inner.append("<p style=\"font-size:13px;\"><a href=\"").append(escape(url))
                    .append("\">Open full report #").append(report.id()).append("</a></p>");
        }
        return new MailBodies(plain.toString(), pageHtml(escape(title), inner.toString()));
    }

    // ---- Single watchlist report ----

    public static MailBodies report(String watchlistName, Long reportId, String summary,
                                    List<ReportService.AnalysisRow> rows, String baseUrl) {
        String title = "Analysis report for " + watchlistName + " (report #" + reportId + ")";
        StringBuilder plain = new StringBuilder(title).append("\n\n").append(summary).append("\n\n");
        StringBuilder inner = new StringBuilder("<p style=\"font-size:14px;\">")
                .append(escape(summary)).append("</p>");
        for (ReportService.AnalysisRow row : rows) {
            var r = row.result();
            plain.append(stockCardPlain(row.stock().getSymbol(), row.stock().getCompanyName(),
                    r.signal(), r.riskLevel(), r.priceTrend(), r.fundamentalTrend(), r.newsImpact(),
                    Boolean.TRUE.equals(r.criticalAlert()), r.summary(), r.keyReasons())).append("\n");
            inner.append(stockCardHtml(row.stock().getSymbol(), row.stock().getCompanyName(),
                    r.signal(), r.riskLevel(), r.priceTrend(), r.fundamentalTrend(), r.newsImpact(),
                    Boolean.TRUE.equals(r.criticalAlert()), r.summary(), r.keyReasons()));
        }
        String url = reportUrl(baseUrl, reportId);
        plain.append("Full report: ").append(url).append("\n");
        inner.append("<p style=\"font-size:13px;\"><a href=\"").append(escape(url))
                .append("\">Open full report #").append(reportId).append("</a></p>");
        return new MailBodies(plain.toString(), pageHtml(escape(title), inner.toString()));
    }

    // ---- Midday MEDIUM roundup ----

    /** One combined mail for today's MEDIUM alerts (HIGH/CRITICAL already mailed instantly). */
    public static MailBodies roundup(String dateLabel, List<Alert> alerts) {
        String title = "Midday roundup for " + dateLabel + " — " + alerts.size() + " medium signals";
        StringBuilder plain = new StringBuilder(title).append("\n\n");
        StringBuilder inner = new StringBuilder();
        for (Alert alert : alerts) {
            String symbol = alert.getStock().getSymbol();
            String company = alert.getStock().getCompanyName();
            String type = String.valueOf(alert.getAlertType());
            String message = alert.getMessage() == null ? "" : alert.getMessage();
            String when = String.valueOf(alert.getCreatedAt());
            plain.append(symbol).append(" — ").append(word(type)).append("\n")
                    .append(message).append("\n")
                    .append(company).append(" | ").append(when).append("\n\n");
            inner.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"margin:8px 0;\"><tr><td style=\"border:1px solid #d0d7de;")
                    .append("border-radius:8px;padding:12px;\">")
                    .append("<div style=\"font-size:15px;margin-bottom:6px;\"><b>")
                    .append(escape(symbol)).append("</b> <span style=\"color:#57606a;\">")
                    .append(escape(company)).append("</span></div>")
                    .append("<div style=\"margin-bottom:8px;\">")
                    .append(riskBadge(String.valueOf(alert.getSeverity()))).append("</div>")
                    .append("<div style=\"font-size:13px;color:#57606a;margin-bottom:8px;\">")
                    .append(escape(word(type))).append(" &middot; ").append(escape(when))
                    .append("</div><div style=\"font-size:14px;line-height:1.5;\">")
                    .append(escape(message)).append("</div></td></tr></table>");
        }
        inner.append("<p style=\"font-size:12px;color:#888888;\">High and critical alerts ")
                .append("were already mailed instantly; the full day's picture arrives ")
                .append("in the 16:00 market-close digest.</p>");
        return new MailBodies(plain.toString(), pageHtml(escape(title), inner.toString()));
    }

    // ---- Urgent HIGH/CRITICAL digest (one mail per run) ----

    /**
     * One combined mail for a monitor/analysis run's HIGH/CRITICAL alerts.
     * Replaces one-mail-per-alert, which turns a busy news day into a mail storm.
     */
    public static MailBodies urgent(String dateLabel, List<Alert> alerts) {
        String title = "Urgent alerts for " + dateLabel + " — " + alerts.size()
                + (alerts.size() == 1 ? " high/critical signal" : " high/critical signals");
        StringBuilder plain = new StringBuilder(title).append("\n\n");
        StringBuilder inner = new StringBuilder();
        for (Alert alert : alerts) {
            String symbol = alert.getStock().getSymbol();
            String company = alert.getStock().getCompanyName();
            String type = String.valueOf(alert.getAlertType());
            String message = alert.getMessage() == null ? "" : alert.getMessage();
            String when = String.valueOf(alert.getCreatedAt());
            plain.append(symbol).append(" — ").append(word(type)).append("\n")
                    .append(message).append("\n")
                    .append(company).append(" | ").append(when).append("\n\n");
            inner.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"margin:8px 0;\"><tr><td style=\"border:1px solid #d0d7de;")
                    .append("border-radius:8px;padding:12px;\">")
                    .append("<div style=\"font-size:15px;margin-bottom:6px;\"><b>")
                    .append(escape(symbol)).append("</b> <span style=\"color:#57606a;\">")
                    .append(escape(company)).append("</span></div>")
                    .append("<div style=\"margin-bottom:8px;\">")
                    .append(riskBadge(String.valueOf(alert.getSeverity()))).append("</div>")
                    .append("<div style=\"font-size:13px;color:#57606a;margin-bottom:8px;\">")
                    .append(escape(word(type))).append(" &middot; ").append(escape(when))
                    .append("</div><div style=\"font-size:14px;line-height:1.5;\">")
                    .append(escape(message)).append("</div></td></tr></table>");
        }
        inner.append("<p style=\"font-size:12px;color:#888888;\">Grouped: one alert per stock ")
                .append("per signal type per day; the full day's picture arrives ")
                .append("in the 16:00 market-close digest.</p>");
        return new MailBodies(plain.toString(), pageHtml(escape(title), inner.toString()));
    }

    // ---- Instant alert ----
    public static MailBodies alert(Alert alert) {
        String symbol = alert.getStock().getSymbol();
        String company = alert.getStock().getCompanyName();
        String severity = String.valueOf(alert.getSeverity());
        String type = String.valueOf(alert.getAlertType());
        String when = String.valueOf(alert.getCreatedAt());
        String message = alert.getMessage() == null ? "" : alert.getMessage();

        String title = severity + " alert: " + symbol + " — " + type;
        String plain = title + "\n\nStock: " + symbol + " (" + company + ", "
                + alert.getStock().getExchange() + ")\nSeverity: " + word(severity)
                + "\nType: " + word(type) + "\nTime: " + when + "\n\n" + message + "\n";

        String inner = "<div style=\"margin-bottom:8px;\">" + riskBadge(severity) + "</div>"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td "
                + "style=\"border:1px solid #d0d7de;border-radius:8px;padding:12px;\">"
                + "<div style=\"font-size:15px;margin-bottom:6px;\"><b>" + escape(symbol)
                + "</b> <span style=\"color:#57606a;\">" + escape(company) + " ("
                + escape(String.valueOf(alert.getStock().getExchange())) + ")</span></div>"
                + "<div style=\"font-size:13px;color:#57606a;margin-bottom:8px;\">Type: "
                + escape(word(type)) + " &middot; Time: " + escape(when) + "</div>"
                + "<div style=\"font-size:14px;line-height:1.5;\">" + escape(message) + "</div>"
                + "</td></tr></table>";
        return new MailBodies(plain, pageHtml(escape(title), inner));
    }
}

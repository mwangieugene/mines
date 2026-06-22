package ke.intraneighbourhood.security.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ContactServlet
 * ---------------
 * Handles POST requests from the website contact form.
 * (Intraneighbourhood Security Agency — Kenya)
 *
 * Endpoint : POST /api/contact
 * Expects : JSON body { fullName, phone, email, serviceType, location, message
 * }
 * Returns : JSON { "status": "ok|error", "message": "..." }
 *
 * On a valid submission this servlet:
 * 1. Validates all fields (including Kenyan phone-number format).
 * 2. Appends the enquiry to enquiries.log on disk.
 * 3. Sends a notification email to the agency inbox.
 * 4. Sends a confirmation / auto-reply email to the client.
 *
 * ── CONFIGURATION ────────────────────────────────────────────────────────────
 * Fill in the five constants below before deploying.
 * Using environment variables or a properties file instead of hard-coding
 * credentials is strongly recommended for production.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@WebServlet("/api/contact")
public class inter extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(inter.class.getName());

    // ── Email configuration — change these before deploying ──────────────────

    /** SMTP host. Gmail shown; swap for your host's SMTP server if needed. */
    private static final String SMTP_HOST = "smtp.gmail.com";

    /** SMTP port. 587 = STARTTLS (recommended). Use 465 for SSL. */
    private static final int SMTP_PORT = 587;

    /**
     * The Gmail address (or other SMTP account) used to send emails.
     * For Gmail you must create an App Password at
     * https://myaccount.google.com/apppasswords — do NOT use your main password.
     */
    private static final String SMTP_USER = "eugenemwangi0@gmail.com";

    /** The App Password generated for the account above. */
    private static final String SMTP_PASSWORD = "ergx emuq ehdm jlev";

    /**
     * The agency inbox that receives enquiry notifications.
     * Usually your company email, e.g. info@intraneighbourhoodsecurity.co.ke
     */
    private static final String AGENCY_EMAIL = "eugenemwangi0@gmail.com";

    // ── Validation patterns ───────────────────────────────────────────────────

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    // Accepts: 07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX, +2541XXXXXXXX
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+254|0)(7|1)\\d{8}$");

    private static final Path LOG_FILE = Path.of("enquiries.log");

    // ── Servlet entry point ───────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // 1 — Parse the JSON body
        String body = readBody(request);
        EnquiryForm form;
        try {
            form = EnquiryForm.fromJson(body);
        } catch (IllegalArgumentException ex) {
            writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Could not read the submitted form. Please try again.");
            return;
        }

        // 2 — Validate fields
        ValidationResult validation = validate(form);
        if (!validation.isValid()) {
            writeJsonError(response, HttpServletResponse.SC_UNPROCESSABLE_ENTITY,
                    validation.errorMessage());
            return;
        }

        // 3 — Persist to log file
        try {
            persistEnquiry(form);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to write enquiry to log file", ex);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "We could not save your request right now. Please call us directly.");
            return;
        }

        // 4 — Send emails (non-fatal: we still confirm to the user if email fails)
        boolean emailSent = true;
        try {
            Session mailSession = buildMailSession();
            sendAgencyNotification(mailSession, form);
            sendClientConfirmation(mailSession, form);
        } catch (MessagingException ex) {
            // Log the failure but don't reject the submission — the enquiry is
            // already saved to disk and the agency can follow up manually.
            LOG.log(Level.WARNING, "Email sending failed for enquiry from " + form.email(), ex);
            emailSent = false;
        }

        // 5 — Respond to the browser
        String note = emailSent
                ? " A confirmation has been sent to " + escapeJson(form.email()) + "."
                : "";

        try (PrintWriter out = response.getWriter()) {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{"
                    + "\"status\":\"ok\","
                    + "\"message\":\"Thank you, " + escapeJson(form.fullName()) + ". "
                    + "Your request for " + escapeJson(serviceLabel(form.serviceType()))
                    + " has been received. Our team will call you on "
                    + escapeJson(form.phone()) + " shortly." + note + "\""
                    + "}");
        }
    }

    // ── Mail helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a JavaMail Session connected to the configured SMTP server
     * using STARTTLS authentication.
     */
    private Session buildMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // Increase timeouts to avoid hanging the servlet thread
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASSWORD);
            }
        });
    }

    /**
     * Sends the enquiry details to the agency inbox so the team can follow up.
     */
    private void sendAgencyNotification(Session session, EnquiryForm form)
            throws MessagingException {

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(SMTP_USER, "Intraneighbourhood Website"));
        msg.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse(AGENCY_EMAIL));
        msg.setSubject("New Enquiry — " + serviceLabel(form.serviceType())
                + " | " + form.fullName());

        String body = "A new service enquiry has been submitted on the website.\n\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "CLIENT DETAILS\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "Name     : " + form.fullName() + "\n"
                + "Phone    : " + form.phone() + "\n"
                + "Email    : " + form.email() + "\n"
                + "Service  : " + serviceLabel(form.serviceType()) + "\n"
                + "Location : " + form.location() + "\n\n"
                + "MESSAGE\n"
                + "─────────────────────────────────────────\n"
                + (isBlank(form.message()) ? "(no additional message)" : form.message()) + "\n\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "Submitted: " + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";

        msg.setText(body, "UTF-8");
        Transport.send(msg);
        LOG.info("Agency notification sent to " + AGENCY_EMAIL);
    }

    /**
     * Sends an auto-reply confirmation to the client so they know
     * their enquiry was received and what to expect next.
     */
    private void sendClientConfirmation(Session session, EnquiryForm form)
            throws MessagingException {

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(SMTP_USER, "Intraneighbourhood Security Agency"));
        msg.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse(form.email()));
        msg.setReplyTo(InternetAddress.parse(AGENCY_EMAIL));
        msg.setSubject("We received your enquiry — " + serviceLabel(form.serviceType()));

        String body = "Dear " + form.fullName() + ",\n\n"
                + "Thank you for contacting Intraneighbourhood Security Agency.\n\n"
                + "We have received your enquiry for:\n"
                + "  Service  : " + serviceLabel(form.serviceType()) + "\n"
                + "  Location : " + form.location() + "\n\n"
                + "One of our team members will call you on " + form.phone()
                + " within the next few hours to discuss your requirements "
                + "and arrange a free site assessment.\n\n"
                + "If you need to reach us urgently in the meantime:\n"
                + "  Phone : +254 7XX XXX XXX\n"
                + "  Email : " + AGENCY_EMAIL + "\n\n"
                + "We look forward to speaking with you.\n\n"
                + "Regards,\n"
                + "Intraneighbourhood Security Agency\n"
                + "Nairobi, Kenya\n"
                + "──────────────────────────────────────────\n"
                + "This is an automated confirmation. Please do not reply directly\n"
                + "to this message — use the contact details above instead.\n";

        msg.setText(body, "UTF-8");
        Transport.send(msg);
        LOG.info("Client confirmation sent to " + form.email());
    }

    // ── Other helpers ─────────────────────────────────────────────────────────

    private String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);
        }
        return sb.toString();
    }

    private ValidationResult validate(EnquiryForm form) {
        if (isBlank(form.fullName()))
            return ValidationResult.invalid("Please enter your full name.");
        if (isBlank(form.phone()) || !PHONE_PATTERN.matcher(form.phone()).matches())
            return ValidationResult.invalid("Please enter a valid Kenyan phone number, e.g. 0712345678.");
        if (isBlank(form.email()) || !EMAIL_PATTERN.matcher(form.email()).matches())
            return ValidationResult.invalid("Please enter a valid email address.");
        if (isBlank(form.serviceType()))
            return ValidationResult.invalid("Please select the service you need.");
        if (isBlank(form.location()))
            return ValidationResult.invalid("Please tell us your property location.");
        return ValidationResult.valid();
    }

    private void persistEnquiry(EnquiryForm form) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = String.format(
                "[%s] name=%s | phone=%s | email=%s | service=%s | location=%s | message=%s%n",
                timestamp, form.fullName(), form.phone(), form.email(),
                form.serviceType(), form.location(),
                isBlank(form.message()) ? "" : form.message().replace("\n", " "));
        Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String serviceLabel(String key) {
        return switch (key) {
            case "guards" -> "Security Guards";
            case "cctv" -> "CCTV Installation";
            case "electric-fence" -> "Electric Fence Installation";
            case "barbed-wire" -> "Barbed Wire Installation";
            case "multiple" -> "Multiple Services";
            default -> "Security Services";
        };
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"status\":\"error\",\"message\":\"" + escapeJson(message) + "\"}");
        }
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    private record EnquiryForm(
            String fullName, String phone, String email,
            String serviceType, String location, String message) {

        static EnquiryForm fromJson(String json) {
            if (json == null || json.isBlank())
                throw new IllegalArgumentException("Empty body");
            return new EnquiryForm(
                    extract(json, "fullName"), extract(json, "phone"),
                    extract(json, "email"), extract(json, "serviceType"),
                    extract(json, "location"), extract(json, "message"));
        }

        private static String extract(String json, String key) {
            var p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
            var m = p.matcher(json);
            return m.find()
                    ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").trim()
                    : "";
        }
    }

    private record ValidationResult(boolean isValid, String errorMessage) {
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
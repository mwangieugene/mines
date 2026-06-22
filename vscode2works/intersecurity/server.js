/**
 * server.js — Intraneighbourhood Security Agency
 * ------------------------------------------------
 * Node.js / Express backend that:
 *   1. Serves the static website (index.html, css/, js/)
 *   2. Handles POST /api/contact from the contact form
 *   3. Validates the submission
 *   4. Appends the enquiry to enquiries.log
 *   5. Sends a notification email to the agency
 *   6. Sends a confirmation email back to the client
 *
 * Run:
 *   node server.js
 *
 * Requires:
 *   npm install express nodemailer
 */

const express = require('express');
const nodemailer = require('nodemailer');
const fs = require('fs');
const path = require('path');

const app = express();

// ── Configuration — fill these in before running ─────────────────────────────

const CONFIG = {
    // SMTP settings — Gmail shown. See README for other providers.
    smtp: {
        host: 'smtp.gmail.com',
        port: 587,                    // 587 = STARTTLS (recommended)
        secure: false,                // true only if using port 465
        user: 'eugenemwangi0@gmail.com',  // ← your Gmail address
        pass: 'ergx emuq ehdm jlev',    // ← Gmail App Password (not your normal password)
        //   Generate one at: myaccount.google.com/apppasswords
    },

    // The agency inbox that receives enquiry notifications
    agencyEmail: 'eugenemwangi0@gmail.com',  // ← agency email

    // Port the server listens on
    port: 3000,
};

// ── Validation helpers ────────────────────────────────────────────────────────

const EMAIL_REGEX = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;

// Kenyan numbers: 07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX, +2541XXXXXXXX
const PHONE_REGEX = /^(\+254|0)(7|1)\d{8}$/;

const SERVICE_LABELS = {
    'guards': 'Security Guards',
    'cctv': 'CCTV Installation',
    'electric-fence': 'Electric Fence Installation',
    'barbed-wire': 'Barbed Wire Installation',
    'multiple': 'Multiple Services',
};

function validate(body) {
    const { fullName, phone, email, serviceType, location } = body;

    if (!fullName?.trim())
        return 'Please enter your full name.';
    if (!phone?.trim() || !PHONE_REGEX.test(phone.trim()))
        return 'Please enter a valid Kenyan phone number, e.g. 0712345678.';
    if (!email?.trim() || !EMAIL_REGEX.test(email.trim()))
        return 'Please enter a valid email address.';
    if (!serviceType || !SERVICE_LABELS[serviceType])
        return 'Please select a valid service.';
    if (!location?.trim())
        return 'Please tell us your property location.';

    return null; // null = valid
}

// ── Nodemailer transporter ────────────────────────────────────────────────────

const transporter = nodemailer.createTransport({
    host: CONFIG.smtp.host,
    port: CONFIG.smtp.port,
    secure: CONFIG.smtp.secure,
    auth: {
        user: CONFIG.smtp.user,
        pass: CONFIG.smtp.pass,
    },
    connectionTimeout: 5000,
    greetingTimeout: 5000,
    socketTimeout: 5000,
});

// ── Email builders ────────────────────────────────────────────────────────────

function buildAgencyEmail(form) {
    const serviceLabel = SERVICE_LABELS[form.serviceType];
    const now = new Date().toLocaleString('en-KE', { timeZone: 'Africa/Nairobi' });

    return {
        from: `"Intraneighbourhood Website" <${CONFIG.smtp.user}>`,
        to: CONFIG.agencyEmail,
        subject: `New Enquiry — ${serviceLabel} | ${form.fullName}`,
        text: `
A new service enquiry has been submitted on the website.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CLIENT DETAILS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Name     : ${form.fullName}
Phone    : ${form.phone}
Email    : ${form.email}
Service  : ${serviceLabel}
Location : ${form.location}

MESSAGE
─────────────────────────────────────────
${form.message?.trim() || '(no additional message)'}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Submitted: ${now} (Nairobi time)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    `.trim(),
    };
}

function buildClientEmail(form) {
    const serviceLabel = SERVICE_LABELS[form.serviceType];

    return {
        from: `"Intraneighbourhood Security Agency" <${CONFIG.smtp.user}>`,
        to: form.email,
        replyTo: CONFIG.agencyEmail,
        subject: `We received your enquiry — ${serviceLabel}`,
        text: `
Dear ${form.fullName},

Thank you for contacting Intraneighbourhood Security Agency.

We have received your enquiry for:
  Service  : ${serviceLabel}
  Location : ${form.location}

One of our team members will call you on ${form.phone} within the next
few hours to discuss your requirements and arrange a free site assessment.

If you need to reach us urgently in the meantime:
  Phone : +254 7XX XXX XXX
  Email : ${CONFIG.agencyEmail}

We look forward to speaking with you.

Regards,
Intraneighbourhood Security Agency
Nairobi, Kenya
──────────────────────────────────────────────
This is an automated confirmation. Please do not reply directly
to this message — use the contact details above instead.
    `.trim(),
    };
}

// ── Log helper ────────────────────────────────────────────────────────────────

function logEnquiry(form) {
    const timestamp = new Date().toISOString();
    const line = `[${timestamp}] name=${form.fullName} | phone=${form.phone} | `
        + `email=${form.email} | service=${form.serviceType} | `
        + `location=${form.location} | message=${(form.message || '').replace(/\n/g, ' ')}\n`;

    fs.appendFile('enquiries.log', line, (err) => {
        if (err) console.error('Failed to write enquiry log:', err.message);
    });
}

// ── Middleware ────────────────────────────────────────────────────────────────

app.use(express.json());

// Serve the static website files from the same folder as this server
app.use(express.static(path.join(__dirname)));

// ── API route ─────────────────────────────────────────────────────────────────

app.post('/api/contact', async (req, res) => {
    // 1 — Validate
    const error = validate(req.body);
    if (error) {
        return res.status(422).json({ status: 'error', message: error });
    }

    const form = {
        fullName: req.body.fullName.trim(),
        phone: req.body.phone.trim(),
        email: req.body.email.trim(),
        serviceType: req.body.serviceType,
        location: req.body.location.trim(),
        message: req.body.message?.trim() || '',
    };

    // 2 — Log to file
    logEnquiry(form);

    // 3 — Send emails
    let emailSent = true;
    try {
        await transporter.sendMail(buildAgencyEmail(form));
        await transporter.sendMail(buildClientEmail(form));
        console.log(`Emails sent for enquiry from ${form.email}`);
    } catch (err) {
        console.error('Email sending failed:', err.message);
        emailSent = false;
        // Don't reject — the enquiry is logged; the agency can follow up manually
    }

    // 4 — Respond
    const note = emailSent
        ? ` A confirmation has been sent to ${form.email}.`
        : '';

    return res.status(200).json({
        status: 'ok',
        message: `Thank you, ${form.fullName}. Your request for `
            + `${SERVICE_LABELS[form.serviceType]} has been received. `
            + `Our team will call you on ${form.phone} shortly.${note}`,
    });
});

// ── Start server ──────────────────────────────────────────────────────────────

app.listen(CONFIG.port, () => {
    console.log(`
  ┌─────────────────────────────────────────────────┐
  │   Intraneighbourhood Security Agency — Server   │
  │   Running at http://localhost:${CONFIG.port}             │
  │   Press Ctrl+C to stop                          │
  └─────────────────────────────────────────────────┘
  `);
});
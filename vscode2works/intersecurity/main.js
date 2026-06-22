// ===== Mobile nav toggle =====
const navToggle = document.getElementById('navToggle');
const mainNav = document.getElementById('mainNav');

navToggle.addEventListener('click', () => {
    const isOpen = mainNav.classList.toggle('open');
    navToggle.setAttribute('aria-expanded', String(isOpen));
});

mainNav.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
        mainNav.classList.remove('open');
        navToggle.setAttribute('aria-expanded', 'false');
    });
});

// ===== Contact form submission =====
// Posts to the Node.js backend (server.js) at /api/contact
const form = document.getElementById('contactForm');
const statusEl = document.getElementById('formStatus');
const submitBtn = document.getElementById('submitBtn');

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const payload = {
        fullName: form.fullName.value.trim(),
        phone: form.phone.value.trim(),
        email: form.email.value.trim(),
        serviceType: form.serviceType.value,
        location: form.location.value.trim(),
        message: form.message.value.trim()
    };

    submitBtn.disabled = true;
    submitBtn.textContent = 'Sending...';
    statusEl.textContent = '';
    statusEl.className = 'form-status';

    try {
        const res = await fetch('/api/contact', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error('Server responded with ' + res.status);

        const data = await res.json();

        statusEl.textContent = data.message || 'Request received. We will contact you shortly.';
        statusEl.classList.add('ok');
        form.reset();
    } catch (err) {
        statusEl.textContent = 'Could not send your request. Please call us directly or try again.';
        statusEl.classList.add('err');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Send Request';
    }
});
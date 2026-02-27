const API_BASE = "/api";  

    let emailVerified = false;
    let isSendingOtp  = false;
    let countdownTimer = null;

    const els = {
        email:        document.getElementById("email"),
        getCodeBtn:   document.getElementById("getCodeBtn"),
        otpSection:   document.getElementById("otpSection"),
        otpInput:     document.getElementById("otpInput"),
        verifyOtpBtn: document.getElementById("verifyOtpBtn"),
        resendLink:   document.getElementById("resendLink"),
        signupBtn:    document.getElementById("signupBtn"),
        signupForm:   document.getElementById("signupForm"),
        successPopup: document.getElementById("successPopup"),
        countdown:    document.getElementById("countdown"),
        status:       document.getElementById("statusMessage"),
    };

    const errors = {
        email:        document.getElementById("emailError"),
        otp:          document.getElementById("otpError"),
        fullName:     document.getElementById("fullNameError"),
        address:      document.getElementById("addressError"),
        phone:        document.getElementById("phoneError"),
        dob:          document.getElementById("dobError"),
        username:     document.getElementById("usernameError"),
        password:     document.getElementById("passwordError"),
        confirm:      document.getElementById("confirmPasswordError"),
    };

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function showError(el, msg) {
        el.textContent = msg;
        el.previousElementSibling?.classList.add("error");
    }

    function clearErrors() {
        Object.values(errors).forEach(e => {
            e.textContent = "";
        });
        document.querySelectorAll(".signup-input.error").forEach(i => i.classList.remove("error"));
        if (els.status) els.status.textContent = "";
    }

    function updateGetCodeButton() {
        const valid = emailRegex.test(els.email.value.trim());
        els.getCodeBtn.style.display = valid ? "block" : "none";
        els.getCodeBtn.disabled = !valid || isSendingOtp;

        if (!valid) {
            els.otpSection.style.display = "none";
            emailVerified = false;
            els.signupBtn.disabled = true;
        }
    }

    els.email.addEventListener("input", updateGetCodeButton);
    els.email.addEventListener("change", updateGetCodeButton);

    async function sendOtp() {
        if (isSendingOtp) return;
        const email = els.email.value.trim();
        if (!emailRegex.test(email)) return;

        isSendingOtp = true;
        els.getCodeBtn.disabled = true;
        els.getCodeBtn.textContent = "Sending...";
        errors.email.textContent = "";

        try {
            const res = await fetch(`${API_BASE}/send-otp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Failed to send code");
            }

            els.otpSection.style.display = "block";
            els.otpInput.value = "";
            els.verifyOtpBtn.disabled = false;
            els.getCodeBtn.textContent = "Resend Code";
            if (els.status) els.status.textContent = data.message || "OTP sent. Check your email.";
        } catch (err) {
            showError(errors.email, err.message || "Could not send verification code");
            if (els.status) els.status.textContent = err.message || "Could not send verification code";
        } finally {
            isSendingOtp = false;
            updateGetCodeButton();
        }
    }

    els.getCodeBtn.addEventListener("click", sendOtp);
    els.resendLink.addEventListener("click", sendOtp);

    els.verifyOtpBtn.addEventListener("click", async () => {
        const otp = els.otpInput.value.trim();
        if (!otp) {
            showError(errors.otp, "Please enter the code");
            return;
        }

        els.verifyOtpBtn.disabled = true;
        errors.otp.textContent = "";

        try {
            const res = await fetch(`${API_BASE}/verify-otp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    email: els.email.value.trim(),
                    otp
                })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Invalid or expired code");
            }

            emailVerified = true;
            els.otpSection.style.display = "none";
            els.signupBtn.disabled = false;
            if (els.status) els.status.textContent = data.message || "OTP verified.";
            
        } catch (err) {
            showError(errors.otp, err.message || "Verification failed");
            els.verifyOtpBtn.disabled = false;
            if (els.status) els.status.textContent = err.message || "Verification failed";
        }
    });

    els.signupForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        clearErrors();

        if (!emailVerified) {
            showError(errors.email, "Please verify your email first");
            if (els.status) els.status.textContent = "Please verify your email first.";
            return;
        }

        const values = {
            fullName:       els.signupForm.fullName.value.trim(),
            address:        els.signupForm.address.value.trim(),
            phone:          els.signupForm.phone.value.trim(),
            dob:            els.signupForm.dob.value,
            username:       els.signupForm.username.value.trim(),
            email:          els.email.value.trim(),
            password:       els.signupForm.password.value,
            confirmPassword: els.signupForm.confirmPassword.value,
        };

        let valid = true;

        if (values.fullName.length < 2) {
            showError(errors.fullName, "Please enter your full name");
            valid = false;
        }
        if (values.address.length < 5) {
            showError(errors.address, "Please enter a valid address");
            valid = false;
        }
        if (values.phone.length < 7) {
            showError(errors.phone, "Please enter a valid phone number");
            valid = false;
        }
        if (!values.dob) {
            showError(errors.dob, "Date of birth is required");
            valid = false;
        } else {
            const age = new Date().getFullYear() - new Date(values.dob).getFullYear();
            if (age < 18) {  
                showError(errors.dob, "You must be at least 18 years old");
                valid = false;
            }
        }
        if (values.username.length < 9) {
            showError(errors.username, "Username must be at least 9 characters");
            valid = false;
        }
        const strongPwd = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,}$/;
        if (!strongPwd.test(values.password)) {
            showError(errors.password, "Password must be 8+ chars with upper, lower, number, special");
            valid = false;
        }
        if (values.confirmPassword !== values.password) {
            showError(errors.confirm, "Passwords do not match");
            valid = false;
        }

        if (!valid) {
            if (els.status) els.status.textContent = "Please fix the errors above.";
            return;
        }

        
        try {
            const res = await fetch(`${API_BASE}/signup`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fullName: values.fullName,
                    address:  values.address,
                    phone:    values.phone,
                    dob:      values.dob,
                    username: values.username,
                    email:    values.email,
                    password: values.password,
                    confirmPassword: values.confirmPassword,
                    
                })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || "Registration failed");
            }

            
            els.successPopup.style.display = "flex";
            let sec = 8;
            els.countdown.textContent = sec;

            countdownTimer = setInterval(() => {
                sec--;
                els.countdown.textContent = sec;
                if (sec <= 0) {
                    clearInterval(countdownTimer);
                    window.location.href = "/login";  
                }
            }, 1000);

        } catch (err) {
            if (els.status) {
                els.status.textContent = err.message || "Something went wrong during registration";
            }
        }
    });

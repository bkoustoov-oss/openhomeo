use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use sha2::{Sha256, Digest};
use argon2::{
    password_hash::{
        rand_core::OsRng,
        PasswordHash, PasswordHasher, PasswordVerifier, SaltString
    },
    Argon2
};

// ============================================================================
// 1. COLOR SPACE (RGB <-> CIE-L*a*b*) & CIEDE2000 DELTA-E
// ============================================================================

/// Converts sRGB (0-255) to CIE-XYZ with D65 illuminant reference
fn rgb_to_xyz(r: f64, g: f64, b: f64) -> (f64, f64, f64) {
    let pivot = |v: f64| -> f64 {
        let v = v / 255.0;
        let p = if v > 0.04045 {
            ((v + 0.055) / 1.055).powf(2.4)
        } else {
            v / 12.92
        };
        p * 100.0
    };

    let r = pivot(r);
    let g = pivot(g);
    let b = pivot(b);

    let x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
    let y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
    let z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;
    (x, y, z)
}

/// Converts CIE-XYZ to CIE-L*a*b* using standard 2° observer D65 white point
pub fn xyz_to_lab(x: f64, y: f64, z: f64) -> (f64, f64, f64) {
    // Reference white D65:
    const XN: f64 = 95.047;
    const YN: f64 = 100.000;
    const ZN: f64 = 108.883;

    let pivot = |v: f64| -> f64 {
        const EPSILON: f64 = 0.008856; // (216/24389)
        const KAPPA: f64 = 903.3;      // (24389/27)
        if v > EPSILON {
            v.cbrt()
        } else {
            (KAPPA * v + 16.0) / 116.0
        }
    };

    let fx = pivot(x / XN);
    let fy = pivot(y / YN);
    let fz = pivot(z / ZN);

    let l = 116.0 * fy - 16.0;
    let a = 500.0 * (fx - fy);
    let b = 200.0 * (fy - fz);
    (l, a, b)
}

pub fn rgb_to_lab_internal(r: u8, g: u8, b: u8) -> (f64, f64, f64) {
    let (x, y, z) = rgb_to_xyz(r as f64, g as f64, b as f64);
    xyz_to_lab(x, y, z)
}

/// Standard CIEDE2000 Color Difference Metric (ISO/CIE 11664-6:2014)
pub fn ciede2000_internal(l1: f64, a1: f64, b1: f64, l2: f64, a2: f64, b2: f64) -> f64 {
    let k_l = 1.0;
    let k_c = 1.0;
    let k_h = 1.0;

    let c1 = (a1.powi(2) + b1.powi(2)).sqrt();
    let c2 = (a2.powi(2) + b2.powi(2)).sqrt();
    let c_bar = (c1 + c2) / 2.0;

    let g = 0.5 * (1.0 - (c_bar.powi(7) / (c_bar.powi(7) + 25.0f64.powi(7))).sqrt());

    let a1_prime = (1.0 + g) * a1;
    let a2_prime = (1.0 + g) * a2;

    let c1_prime = (a1_prime.powi(2) + b1.powi(2)).sqrt();
    let c2_prime = (a2_prime.powi(2) + b2.powi(2)).sqrt();

    let h1_prime = if a1_prime == 0.0 && b1 == 0.0 {
        0.0
    } else {
        let deg = b1.atan2(a1_prime).to_degrees();
        if deg >= 0.0 { deg } else { deg + 360.0 }
    };

    let h2_prime = if a2_prime == 0.0 && b2 == 0.0 {
        0.0
    } else {
        let deg = b2.atan2(a2_prime).to_degrees();
        if deg >= 0.0 { deg } else { deg + 360.0 }
    };

    let delta_l_prime = l2 - l1;
    let delta_c_prime = c2_prime - c1_prime;

    let h_diff = (h2_prime - h1_prime).abs();
    let delta_h_prime = if c1_prime * c2_prime == 0.0 {
        0.0
    } else if h_diff <= 180.0 {
        h2_prime - h1_prime
    } else if h2_prime <= h1_prime {
        h2_prime - h1_prime + 360.0
    } else {
        h2_prime - h1_prime - 360.0
    };

    let delta_cap_h_prime = 2.0 * (c1_prime * c2_prime).sqrt() * (delta_h_prime.to_radians() / 2.0).sin();

    let l_bar_prime = (l1 + l2) / 2.0;
    let c_bar_prime = (c1_prime + c2_prime) / 2.0;

    let h_bar_prime = if c1_prime * c2_prime == 0.0 {
        h1_prime + h2_prime
    } else if h_diff <= 180.0 {
        (h1_prime + h2_prime) / 2.0
    } else if h1_prime + h2_prime < 360.0 {
        (h1_prime + h2_prime + 360.0) / 2.0
    } else {
        (h1_prime + h2_prime - 360.0) / 2.0
    };

    let t = 1.0
        - 0.17 * (h_bar_prime - 30.0).to_radians().cos()
        + 0.24 * (2.0 * h_bar_prime).to_radians().cos()
        + 0.32 * (3.0 * h_bar_prime + 6.0).to_radians().cos()
        - 0.20 * (4.0 * h_bar_prime - 63.0).to_radians().cos();

    let delta_theta = 30.0 * (-((h_bar_prime - 275.0) / 25.0).powi(2)).exp();
    let r_c = 2.0 * (c_bar_prime.powi(7) / (c_bar_prime.powi(7) + 25.0f64.powi(7))).sqrt();
    let s_l = 1.0 + (0.015 * (l_bar_prime - 50.0).powi(2)) / (20.0 + (l_bar_prime - 50.0).powi(2)).sqrt();
    let s_c = 1.0 + 0.045 * c_bar_prime;
    let s_h = 1.0 + 0.015 * c_bar_prime * t;
    let r_t = -r_c * (2.0 * delta_theta.to_radians()).sin();

    let term_l = delta_l_prime / (k_l * s_l);
    let term_c = delta_c_prime / (k_c * s_c);
    let term_h = delta_cap_h_prime / (k_h * s_h);

    (term_l.powi(2) + term_c.powi(2) + term_h.powi(2) + r_t * term_c * term_h).sqrt()
}

// ============================================================================
// 2. CRYPTOGRAPHY (Argon2id & SHA-256)
// ============================================================================

pub fn hash_password_argon2id(password: &str) -> Result<String, String> {
    let salt = SaltString::generate(&mut OsRng);
    let argon2 = Argon2::default();
    match argon2.hash_password(password.as_bytes(), &salt) {
        Ok(hash) => Ok(hash.to_string()),
        Err(e) => Err(e.to_string()),
    }
}

pub fn verify_password_argon2id(password: &str, hash_str: &str) -> bool {
    match PasswordHash::new(hash_str) {
        Ok(parsed_hash) => Argon2::default().verify_password(password.as_bytes(), &parsed_hash).is_ok(),
        Err(_) => false,
    }
}

pub fn sha256_digest(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    let result = hasher.finalize();
    hex_encode(&result)
}

fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

// ============================================================================
// 3. C-ABI EXPORTS (FOR PROJECT PANAMA FFM / JNI)
// ============================================================================

#[no_mangle]
pub extern "C" fn openhomeo_rgb_to_lab(
    r: u8,
    g: u8,
    b: u8,
    out_l: *mut f64,
    out_a: *mut f64,
    out_b: *mut f64,
) {
    if out_l.is_null() || out_a.is_null() || out_b.is_null() {
        return;
    }
    let (l, a, b_val) = rgb_to_lab_internal(r, g, b);
    unsafe {
        *out_l = l;
        *out_a = a;
        *out_b = b_val;
    }
}

#[no_mangle]
pub extern "C" fn openhomeo_ciede2000(
    l1: f64,
    a1: f64,
    b1: f64,
    l2: f64,
    a2: f64,
    b2: f64,
) -> f64 {
    ciede2000_internal(l1, a1, b1, l2, a2, b2)
}

#[no_mangle]
pub extern "C" fn openhomeo_compute_image_lab_mean(
    rgb_ptr: *const u8,
    pixel_count: usize,
    out_l: *mut f64,
    out_a: *mut f64,
    out_b: *mut f64,
) -> i32 {
    if rgb_ptr.is_null() || pixel_count == 0 || out_l.is_null() || out_a.is_null() || out_b.is_null() {
        return -1;
    }

    let slice = unsafe { std::slice::from_raw_parts(rgb_ptr, pixel_count * 3) };
    let mut sum_l = 0.0;
    let mut sum_a = 0.0;
    let mut sum_b = 0.0;

    for i in 0..pixel_count {
        let r = slice[i * 3];
        let g = slice[i * 3 + 1];
        let b = slice[i * 3 + 2];
        let (l, a, b_val) = rgb_to_lab_internal(r, g, b);
        sum_l += l;
        sum_a += a;
        sum_b += b_val;
    }

    let n = pixel_count as f64;
    unsafe {
        *out_l = sum_l / n;
        *out_a = sum_a / n;
        *out_b = sum_b / n;
    }
    0
}

#[no_mangle]
pub extern "C" fn openhomeo_argon2_hash(
    password_ptr: *const c_char,
    out_buf: *mut c_char,
    max_len: usize,
) -> i32 {
    if password_ptr.is_null() || out_buf.is_null() || max_len == 0 {
        return -1;
    }
    let c_str = unsafe { CStr::from_ptr(password_ptr) };
    let password = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    match hash_password_argon2id(password) {
        Ok(hash) => {
            let hash_c = match CString::new(hash) {
                Ok(c) => c,
                Err(_) => return -3,
            };
            let bytes = hash_c.as_bytes_with_nul();
            if bytes.len() > max_len {
                return -4;
            }
            unsafe {
                std::ptr::copy_nonoverlapping(bytes.as_ptr() as *const c_char, out_buf, bytes.len());
            }
            0
        }
        Err(_) => -5,
    }
}

#[no_mangle]
pub extern "C" fn openhomeo_argon2_verify(
    password_ptr: *const c_char,
    hash_ptr: *const c_char,
) -> i32 {
    if password_ptr.is_null() || hash_ptr.is_null() {
        return 0;
    }
    let pass_str = match unsafe { CStr::from_ptr(password_ptr) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };
    let hash_str = match unsafe { CStr::from_ptr(hash_ptr) }.to_str() {
        Ok(s) => s,
        Err(_) => return 0,
    };

    if verify_password_argon2id(pass_str, hash_str) { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn openhomeo_sha256(
    data_ptr: *const u8,
    data_len: usize,
    out_hex_buf: *mut c_char,
    max_len: usize,
) -> i32 {
    if data_ptr.is_null() || out_hex_buf.is_null() || max_len < 65 {
        return -1;
    }
    let data = unsafe { std::slice::from_raw_parts(data_ptr, data_len) };
    let hash = sha256_digest(data);
    let hash_c = match CString::new(hash) {
        Ok(c) => c,
        Err(_) => return -2,
    };
    unsafe {
        std::ptr::copy_nonoverlapping(hash_c.as_ptr(), out_hex_buf, 65);
    }
    0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rgb_to_lab() {
        // Pure White (255, 255, 255) -> L* should be ~100.0, a* ~0.0, b* ~0.0
        let (l, a, b) = rgb_to_lab_internal(255, 255, 255);
        assert!((l - 100.0).abs() < 0.1);
        assert!(a.abs() < 0.1);
        assert!(b.abs() < 0.1);

        // Pure Black (0, 0, 0) -> L* should be ~0.0
        let (l, a, b) = rgb_to_lab_internal(0, 0, 0);
        assert!(l.abs() < 0.1);
        assert!(a.abs() < 0.1);
        assert!(b.abs() < 0.1);
    }

    #[test]
    fn test_ciede2000() {
        // Identical colors should have Delta-E == 0
        let delta = ciede2000_internal(50.0, 20.0, 30.0, 50.0, 20.0, 30.0);
        assert!(delta.abs() < 1e-6);

        // Redness shift (e.g. erythema reduction)
        let before_flare = (55.0, 45.0, 20.0);
        let after_treatment = (60.0, 25.0, 15.0);
        let delta_flare = ciede2000_internal(
            before_flare.0, before_flare.1, before_flare.2,
            after_treatment.0, after_treatment.1, after_treatment.2,
        );
        assert!(delta_flare > 5.0, "Delta-E should detect distinct clinical color shift");
    }

    #[test]
    fn test_argon2id_flow() {
        let password = "DrHomeopathSecurePass2026!";
        let hash = hash_password_argon2id(password).expect("Hash must succeed");
        assert!(hash.starts_with("$argon2id$"));
        assert!(verify_password_argon2id(password, &hash));
        assert!(!verify_password_argon2id("WrongPassword", &hash));
    }

    #[test]
    fn test_sha256() {
        let data = b"OpenHomeo Clinical Image Asset Payload";
        let hash = sha256_digest(data);
        assert_eq!(hash.len(), 64);
    }
}

package com.meridianair.ams.auth;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.BackupCodesResponse;
import com.meridianair.ams.dto.MfaSetupResponse;
import com.meridianair.ams.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class MfaService {

    private static final String ISSUER = "Meridian Air";
    private static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(UserRepository userRepository, TotpService totpService,
                       PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /** Generates a new secret and stores it un-activated - mfaEnabled
     * only flips true once the user proves they can generate a valid
     * code from it, via enable(). */
    public MfaSetupResponse startSetup(User user) {
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);

        auditService.log(AuditLog.builder()
                .userId(user.getId())
                .eventType("mfa.secret_generated")
                .eventCategory("security")
                .action("create")
                .resourceType("mfa")
                .status("success")
                .message("TOTP secret generated"));

        String uri = totpService.buildOtpAuthUri(secret, user.getEmail(), ISSUER);
        return new MfaSetupResponse(secret, uri);
    }

    public boolean enable(User user, String code) {
        if (user.getMfaSecret() == null || !totpService.verifyCode(user.getMfaSecret(), code)) {
            auditService.log(AuditLog.builder()
                    .userId(user.getId()).eventType("mfa.enable_failed").eventCategory("security")
                    .action("update").resourceType("user").status("failure")
                    .message("MFA enable rejected - invalid code"));
            return false;
        }
        user.setMfaEnabled(true);
        userRepository.save(user);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("mfa.enabled").eventCategory("security")
                .action("update").resourceType("user").status("success")
                .message("MFA enabled"));
        return true;
    }

    public void disable(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setBackupCodeHashes(new ArrayList<>());
        userRepository.save(user);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("mfa.disabled").eventCategory("security")
                .action("update").resourceType("user").status("success")
                .message("MFA disabled"));
    }

    public boolean verifyTotp(User user, String code) {
        boolean verified = user.getMfaSecret() != null && totpService.verifyCode(user.getMfaSecret(), code);
        auditService.log(AuditLog.builder()
                .userId(user.getId())
                .eventType(verified ? "mfa.totp_verified" : "mfa.totp_failed")
                .eventCategory("security").action("verify").resourceType("mfa")
                .status(verified ? "success" : "failure"));
        return verified;
    }

    /** Regenerates the full set - any previously issued codes are
     * invalidated, matching how every major provider treats this. */
    public BackupCodesResponse generateBackupCodes(User user) {
        List<String> plaintext = new ArrayList<>(BACKUP_CODE_COUNT);
        List<String> hashes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = randomBackupCode();
            plaintext.add(code);
            hashes.add(passwordEncoder.encode(code));
        }
        user.setBackupCodeHashes(hashes);
        userRepository.save(user);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("mfa.backup_codes_generated").eventCategory("security")
                .action("create").resourceType("mfa").status("success"));

        return new BackupCodesResponse(plaintext);
    }

    /** One-time use: the matched code's hash is removed from the user's
     * list on a successful verify. */
    public boolean verifyAndConsumeBackupCode(User user, String code) {
        List<String> hashes = user.getBackupCodeHashes();
        for (String hash : hashes) {
            if (passwordEncoder.matches(code, hash)) {
                hashes.remove(hash);
                userRepository.save(user);
                auditService.log(AuditLog.builder()
                        .userId(user.getId()).eventType("mfa.backup_code_used").eventCategory("security")
                        .action("verify").resourceType("mfa").status("success"));
                return true;
            }
        }
        return false;
    }

    private String randomBackupCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(BACKUP_CODE_ALPHABET.charAt(secureRandom.nextInt(BACKUP_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}

package com.meridianair.ams.web;

import com.meridianair.ams.auth.MfaService;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.BackupCodesResponse;
import com.meridianair.ams.dto.MfaCodeRequest;
import com.meridianair.ams.dto.MfaSetupResponse;
import com.meridianair.ams.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final CurrentUserProvider currentUserProvider;

    public MfaController(MfaService mfaService, CurrentUserProvider currentUserProvider) {
        this.mfaService = mfaService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/setup")
    public MfaSetupResponse setup() {
        User user = currentUserProvider.require();
        return mfaService.startSetup(user);
    }

    @PostMapping("/enable")
    public void enable(@Valid @RequestBody MfaCodeRequest request) {
        User user = currentUserProvider.require();
        if (!mfaService.enable(user, request.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
    }

    @PostMapping("/disable")
    public void disable() {
        mfaService.disable(currentUserProvider.require());
    }

    @PostMapping("/backup-codes")
    public BackupCodesResponse generateBackupCodes() {
        return mfaService.generateBackupCodes(currentUserProvider.require());
    }
}

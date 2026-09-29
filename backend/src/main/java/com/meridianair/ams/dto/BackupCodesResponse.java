package com.meridianair.ams.dto;

import java.util.List;

/** Plaintext codes, shown to the user exactly once at generation time -
 * the backend only ever persists their hashes afterward. */
public record BackupCodesResponse(List<String> codes) {}

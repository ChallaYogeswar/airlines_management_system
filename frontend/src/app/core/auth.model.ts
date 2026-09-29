export interface UserSummary {
  id: string;
  email: string;
  name: string;
  roles: string[];
  mfaEnabled: boolean;
}

export interface LoginResult {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserSummary;
  requiresMfa: boolean;
  mfaChallenge: string | null;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

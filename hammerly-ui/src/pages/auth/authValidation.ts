export type AuthFormData = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  agreeTerms: boolean;
};

export type AuthFieldErrors = Partial<Record<keyof AuthFormData, string>>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^[0-9+() .-]{7,32}$/;

export const normalizeEmail = (email: string) => email.trim().toLocaleLowerCase();

export const validateLogin = (data: AuthFormData): AuthFieldErrors => {
  const errors: AuthFieldErrors = {};
  const email = normalizeEmail(data.email);
  if (!email) errors.email = 'Email is required.';
  else if (email.length > 254 || !EMAIL_PATTERN.test(email)) errors.email = 'Enter a valid email address.';
  if (!data.password) errors.password = 'Password is required.';
  else if (data.password.length > 128) errors.password = 'Password must be 128 characters or fewer.';
  return errors;
};

export const validateRegistration = (data: AuthFormData): AuthFieldErrors => {
  const errors = validateLogin(data);
  const firstName = data.firstName.trim();
  const lastName = data.lastName.trim();
  const phone = data.phone.trim();

  if (!firstName) errors.firstName = 'First name is required.';
  else if (firstName.length > 100) errors.firstName = 'First name must be 100 characters or fewer.';
  if (!lastName) errors.lastName = 'Last name is required.';
  else if (lastName.length > 100) errors.lastName = 'Last name must be 100 characters or fewer.';
  if (!phone) errors.phone = 'Phone number is required.';
  else if (!PHONE_PATTERN.test(phone)) errors.phone = 'Enter a valid phone number.';
  if (data.password && data.password.length < 8) errors.password = 'Password must be at least 8 characters.';
  if (data.password !== data.confirmPassword) errors.confirmPassword = 'Passwords do not match.';
  if (!data.confirmPassword) errors.confirmPassword = 'Please confirm your password.';
  if (!data.agreeTerms) errors.agreeTerms = 'You must accept the Terms and Privacy Policy.';
  return errors;
};

import { request } from './client';

export const login = (email, password) => request('/Auth/login', { method: 'POST', body: { email, password } });
export const register = (email, password) => request('/Auth/register', { method: 'POST', body: { email, password } });
export const sendSignupOtp = (email) => request('/Auth/send-otp', { method: 'POST', body: { email } });
export const verifySignupOtp = (email, otp) => request('/Auth/verify-otp', { method: 'POST', body: { email, otp } });
export const logout = () => request('/Auth/logout', { method: 'POST' });

/* Admin / manager */
export const listUsers = (page = 0, size = 8) => request('/admin/auth/users', { query: { page, size } });
export const updateUserStatus = (email, status) => request('/admin/auth/users/status', { method: 'PATCH', body: { email, status } });
export const assignRole = (email, role) => request('/admin/auth/users/roles/assign', { method: 'POST', body: { email, role } });
export const revokeRole = (email, role) => request('/admin/auth/users', { method: 'POST', body: { email, role } });

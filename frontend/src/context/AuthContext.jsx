import React, { createContext, useContext, useState } from 'react';
import { authApi } from '../api';

const AuthContext = createContext(null);

const persist = (user) => {
  if (user) localStorage.setItem('user', JSON.stringify(user));
  else localStorage.removeItem('user');
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem('user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [token, setToken] = useState(() => localStorage.getItem('token'));

  const login = (authData) => {
    const nextUser = {
      username: authData.username,
      email: authData.email,
      role: authData.role,
      userId: authData.userId,
    };
    setToken(authData.token);
    setUser(nextUser);
    localStorage.setItem('token', authData.token);
    persist(nextUser);
  };

  const updateUser = (patch) => {
    setUser((prev) => {
      const next = { ...(prev || {}), ...patch };
      persist(next);
      return next;
    });
  };

  const refreshProfile = async () => {
    try {
      const res = await authApi.me();
      const data = res.data || {};
      const patch = {
        username: data.username || user?.username,
        email: data.email || user?.email,
        role: data.role || user?.role,
        userId: data.id || user?.userId,
        fullName: data.fullName,
        studentId: data.studentId,
        studentCode: data.studentCode,
        lecturerId: data.lecturerId,
        lecturerCode: data.lecturerCode,
        department: data.department,
      };
      setUser((prev) => {
        const next = { ...(prev || {}), ...patch };
        persist(next);
        return next;
      });
      return patch;
    } catch {
      return null;
    }
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    persist(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, updateUser, refreshProfile, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);

// Application Constants & Configuration Tokens

export const API_BASE_URL = (typeof window !== 'undefined' && (window.API_BASE_URL || (window.CONFIG && window.CONFIG.API_BASE_URL))) || 'http://localhost:8088/api';

export const USER_ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  CASHIER: 'CASHIER',
  KITCHEN_STAFF: 'KITCHEN_STAFF',
  INVENTORY_MANAGER: 'INVENTORY_MANAGER',
  VIEWER: 'VIEWER'
};

export const PERMISSIONS = {
  VIEW_DASHBOARD: 'VIEW_DASHBOARD',
  MANAGE_BATCHES: 'MANAGE_BATCHES',
  MANAGE_PRICING: 'MANAGE_PRICING',
  RUN_SIMULATOR: 'RUN_SIMULATOR',
  MANAGE_USERS: 'MANAGE_USERS',
  VIEW_REPORTS: 'VIEW_REPORTS',
  MANAGE_SETTINGS: 'MANAGE_SETTINGS',
  TRIGGER_MARKET_CRASH: 'TRIGGER_MARKET_CRASH',
  POS_CHECKOUT: 'POS_CHECKOUT'
};

export const DEFAULT_PRICING_CONFIG = {
  weightVelocity: 0.40,
  weightStockPressure: 0.40,
  weightTimeFactor: 0.20,
  cooldownMinutes: 10,
  minFloorPrice: 18.00,
  maxCeilingPrice: 25.00
};

export const BROADCAST_CHANNEL_NAME = 'pubexchange_market_channel';

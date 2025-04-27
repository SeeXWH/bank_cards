package com.example.bank_cards.serviceInterface;

public interface CardMaintenanceServiceImpl {

    void expireOverdueCards();

    void resetDailyLimits();

    void resetMonthlyLimits();
}
package com.ruskserver.moveearth_addtional.s2.recovery;

import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Nation-account operations shared by upkeep, recovery and dispatch. */
public final class EconomyGateway {
    private EconomyGateway() { }

    public static long balance(MinecraftServer server, UUID nationId) {
        return EconomyLedgerSavedData.get(server).balance(Account.nation(nationId));
    }

    public static Result withdraw(MinecraftServer server, UUID nationId, long amount) {
        return withdraw(server, nationId, amount, UUID.randomUUID(), "nation_expense");
    }

    public static Result withdraw(MinecraftServer server, UUID nationId, long amount,
                                  UUID transactionId, String reason) {
        if (amount < 0L || nationId == null) return Result.INVALID_AMOUNT;
        if (amount == 0L) return Result.SUCCESS;
        return map(EconomyLedgerSavedData.get(server).transfer(transactionId,
                Account.nation(nationId), null, amount, reason));
    }

    public static Result deposit(MinecraftServer server, UUID nationId, long amount) {
        return deposit(server, nationId, amount, UUID.randomUUID(), "nation_income");
    }

    public static Result deposit(MinecraftServer server, UUID nationId, long amount,
                                 UUID transactionId, String reason) {
        if (amount < 0L || nationId == null) return Result.INVALID_AMOUNT;
        if (amount == 0L) return Result.SUCCESS;
        return map(EconomyLedgerSavedData.get(server).transfer(transactionId,
                null, Account.nation(nationId), amount, reason));
    }

    private static Result map(EconomyLedgerSavedData.Result result) {
        return switch (result) {
            case APPLIED, ALREADY_APPLIED -> Result.SUCCESS;
            case INSUFFICIENT_FUNDS -> Result.INSUFFICIENT_FUNDS;
            case INVALID -> Result.INVALID_AMOUNT;
            case OVERFLOW, CONFLICT -> Result.ERROR;
        };
    }

    public enum Result { SUCCESS, INVALID_AMOUNT, INSUFFICIENT_FUNDS, ERROR }
}

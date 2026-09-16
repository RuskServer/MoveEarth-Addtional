package com.ruskserver.moveearth_addtional.s2.recovery;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepSavedData;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Narrow adapter around the external bank API. Every caller must journal before invoking it. */
public final class EconomyGateway {
    private EconomyGateway() { }

    public static Result withdraw(MinecraftServer server, UUID nationId, long amount) {
        if (amount < 0L) return Result.INVALID_AMOUNT;
        if (amount == 0L) return Result.SUCCESS;
        try {
            IBankAccount account = account(server, nationId);
            if (account == null) return Result.ACCOUNT_MISSING;
            MoneyValue value = value(amount);
            if (value.isEmpty() || !account.getStoredMoney().containsValue(value)) return Result.INSUFFICIENT_FUNDS;
            return BankAPI.getApi().BankWithdrawFromServer(account, value).getFirst()
                    ? Result.SUCCESS : Result.INSUFFICIENT_FUNDS;
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Recovery economy withdrawal failed for nation {}", nationId, exception);
            return Result.ERROR;
        }
    }

    public static Result deposit(MinecraftServer server, UUID nationId, long amount) {
        if (amount < 0L) return Result.INVALID_AMOUNT;
        if (amount == 0L) return Result.SUCCESS;
        try {
            IBankAccount account = account(server, nationId);
            if (account == null) return Result.ACCOUNT_MISSING;
            return BankAPI.getApi().BankDepositFromServer(account, value(amount)) ? Result.SUCCESS : Result.ERROR;
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Recovery economy deposit failed for nation {}", nationId, exception);
            return Result.ERROR;
        }
    }

    private static IBankAccount account(MinecraftServer server, UUID nationId) {
        BankReference reference = NationUpkeepSavedData.get(server).state(nationId).reference();
        return reference != null && reference.isValid() ? reference.get() : null;
    }

    private static MoneyValue value(long amount) {
        return MoneyValueParser.ParseConfigString(
                "coin;" + amount + "-lightmanscurrency:coin_gold", MoneyValue::empty);
    }

    public enum Result { SUCCESS, INVALID_AMOUNT, ACCOUNT_MISSING, INSUFFICIENT_FUNDS, ERROR }
}

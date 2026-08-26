package com.ruskserver.moveearth_addtional.client.gunpack;

import java.net.URI;
import java.util.List;

public record RequiredGunPack(String namespace, String displayName, URI downloadPage) {
    public static final List<RequiredGunPack> ALL = List.of(
            new RequiredGunPack(
                    "fmic",
                    "FMIC-WolfeinRace GunsPack",
                    URI.create("https://www.curseforge.com/minecraft/customization/tacz-fmic-wolfeinrace-gunspack/files/8220715")),
            new RequiredGunPack(
                    "cib",
                    "Charge into Battle: Reboot Pack",
                    URI.create("https://www.curseforge.com/minecraft/customization/tacz-charge-into-battle-reboot-pack/files/7280896")),
            new RequiredGunPack(
                    "ccrp",
                    "TaCZ: Classics Reborn",
                    URI.create("https://www.curseforge.com/minecraft/customization/tacz-classics-reborn/files/7529068"))
    );
}

package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.NBTIngredient;
import com.tacz.guns.init.ModRecipe;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * サーバー完全起動後に TaCZ のガンスミスレシピを動的に読み取り、
 * Create の粉砕ホイールレシピとして自動注入するハンドラー。
 * 銃の素材を 60% 還元し、端数は確率ドロップとして処理する。
 * ServerStartedEvent を使用することで、TaCZ の GunPack ロード完了後に処理を行う。
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public class GunDisassemblyRecipeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GunDisassemblyRecipeHandler.class);
    private static final double RETURN_RATE = 0.6;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            injectCrushingRecipes(event.getServer().getRecipeManager());
        } catch (Exception e) {
            LOGGER.error("[MoveEarth] 銃解体レシピの動的注入に失敗しました", e);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // A null player means a full datapack reload. The vanilla reload creates
        // fresh recipe maps, so regenerate the dynamic recipes before they are
        // synchronized to every client. Joining one player needs no regeneration.
        if (event.getPlayer() != null) return;
        try {
            injectCrushingRecipes(event.getPlayerList().getServer().getRecipeManager());
        } catch (Exception e) {
            LOGGER.error("[MoveEarth] データパック再読み込み後の銃解体レシピ注入に失敗しました", e);
        }
    }

    private static void injectCrushingRecipes(RecipeManager recipeManager) throws Exception {
        // TimelessAPI.getAllRecipes() の代わりに、RecipeManager に登録済みの GunSmithTableRecipe を直接取得する
        // TaCZ はデータパックリロード経由でレシピを RecipeManager に登録するため、
        // ServerStarted 時点では確実にロードが完了している
        List<RecipeHolder<GunSmithTableRecipe>> gunRecipeHolders;
        try {
            RecipeType<GunSmithTableRecipe> gunRecipeType = getGunSmithTableRecipeType();
            if (gunRecipeType == null) {
                LOGGER.warn("[MoveEarth] GunSmithTableRecipe の RecipeType が見つかりませんでした。");
                return;
            }
            gunRecipeHolders = recipeManager.getAllRecipesFor(gunRecipeType);
        } catch (Exception e) {
            LOGGER.warn("[MoveEarth] GunSmithTableRecipe の取得に失敗しました: {}", e.getMessage());
            return;
        }

        if (gunRecipeHolders == null || gunRecipeHolders.isEmpty()) {
            LOGGER.warn("[MoveEarth] TaCZ の銃レシピが RecipeManager に見つかりませんでした。TaCZ の GunPack がロードされているか確認してください。");
            return;
        }

        Map<ResourceLocation, RecipeHolder<?>> newRecipes = new LinkedHashMap<>();

        for (RecipeHolder<GunSmithTableRecipe> holder : gunRecipeHolders) {
            GunSmithTableRecipe gunRecipe = holder.value();

            // GunSmithTableRecipe also contains ammo and attachment recipes.
            // Only a result recognized by TaCZ as an actual gun is eligible.
            if (gunRecipe.getResult() == null) continue;
            ItemStack gunStack = gunRecipe.getResult().getResult();
            if (gunStack == null || gunStack.isEmpty()) continue;
            IGun gunItem = IGun.getIGunOrNull(gunStack);
            if (gunItem == null) continue;
            ResourceLocation gunId = gunItem.getGunId(gunStack);
            if (gunId == null) continue;

            // 素材の集計 (同じアイテムは合算)
            Map<Ingredient, Integer> materialMap = new HashMap<>();
            List<GunSmithTableIngredient> inputs = gunRecipe.getInputs();
            if (inputs == null || inputs.isEmpty()) continue;

            for (GunSmithTableIngredient input : inputs) {
                Ingredient ing = input.getIngredient();
                int count = input.getCount();
                // Ingredientをキーにするため、等価比較は難しいので
                // 同じItemStackをもつIngredientsを累積する
                boolean merged = false;
                for (Map.Entry<Ingredient, Integer> e : materialMap.entrySet()) {
                    // 同じアイテムかどうかの簡易比較
                    if (ingredientItemsMatch(e.getKey(), ing)) {
                        materialMap.put(e.getKey(), e.getValue() + count);
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    materialMap.put(ing, count);
                }
            }

            // 各素材について 60% 還元のアウトプットを構築
            NonNullList<ProcessingOutput> outputs = NonNullList.create();
            for (Map.Entry<Ingredient, Integer> mat : materialMap.entrySet()) {
                double returnAmount = mat.getValue() * RETURN_RATE;
                int guaranteed = (int) returnAmount;
                float chance = (float) (returnAmount - guaranteed);

                // 確定ドロップ
                if (guaranteed > 0) {
                    var stacks = mat.getKey().getItems();
                    if (stacks.length > 0) {
                        var stack = stacks[0].copyWithCount(guaranteed);
                        outputs.add(new ProcessingOutput(stack, 1.0f));
                    }
                }
                // 確率ドロップ（端数）
                if (chance > 0.001f) {
                    var stacks = mat.getKey().getItems();
                    if (stacks.length > 0) {
                        var stack = stacks[0].copyWithCount(1);
                        outputs.add(new ProcessingOutput(stack, chance));
                    }
                }
            }

            if (outputs.isEmpty()) continue;

            // CrushingRecipe を構築（ProcessingRecipeParamsはprotectedなのでリフレクションでインスタンス化）
            ProcessingRecipeParams params;
            try {
                var constructor = ProcessingRecipeParams.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                params = constructor.newInstance();
            } catch (Exception ex) {
                LOGGER.warn("[MoveEarth] ProcessingRecipeParams のインスタンス化に失敗（銃ID: {}）: {}", gunId, ex.getMessage());
                continue;
            }

            // Match only the GunId inside TaCZ custom data. Matching the item
            // alone would make every TaCZ gun use whichever recipe is found first;
            // matching all components would reject used guns with ammo or attachments.
            Field ingredientsField = ProcessingRecipeParams.class.getDeclaredField("ingredients");
            ingredientsField.setAccessible(true);
            NonNullList<Ingredient> ingredientList = NonNullList.create();
            CompoundTag gunIdTag = new CompoundTag();
            gunIdTag.putString(GunItemDataAccessor.GUN_ID_TAG, gunId.toString());
            ingredientList.add(new NBTIngredient(
                    HolderSet.direct(gunStack.getItemHolder()), gunIdTag, true).toVanilla());
            ingredientsField.set(params, ingredientList);

            // results フィールドに出力アイテムをセット
            Field resultsField = ProcessingRecipeParams.class.getDeclaredField("results");
            resultsField.setAccessible(true);
            resultsField.set(params, outputs);

            // processingDuration: 10秒 = 200 ticks
            Field durationField = ProcessingRecipeParams.class.getDeclaredField("processingDuration");
            durationField.setAccessible(true);
            durationField.set(params, 200);

            CrushingRecipe crushingRecipe = new CrushingRecipe(params);
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    Moveearth_addtional.MODID,
                    "disassemble_" + gunId.getNamespace() + "_" + gunId.getPath()
            );
            newRecipes.put(recipeId, new RecipeHolder<>(recipeId, crushingRecipe));
        }

        if (newRecipes.isEmpty()) {
            LOGGER.warn("[MoveEarth] 生成できた解体レシピが 0 件でした。");
            return;
        }

        // RecipeManager の内部マップに強制的に追記
        injectIntoRecipeManager(recipeManager, newRecipes);
        LOGGER.info("[MoveEarth] TaCZ 銃解体レシピを {} 件動的に注入しました。", newRecipes.size());
    }

    private static void injectIntoRecipeManager(RecipeManager recipeManager,
                                                Map<ResourceLocation, RecipeHolder<?>> newRecipes) {
        // 1.21.1 exposes stable public accessors for this operation. Rebuild by
        // recipe ID so startup/reload hooks are idempotent and cannot create
        // duplicate-key failures if another synchronization happens.
        Map<ResourceLocation, RecipeHolder<?>> allRecipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (!isGeneratedDisassemblyRecipe(holder.id())) {
                allRecipes.put(holder.id(), holder);
            }
        }
        allRecipes.putAll(newRecipes);
        recipeManager.replaceRecipes(allRecipes.values());

        LOGGER.info("[MoveEarth] 銃解体レシピを含め、全 {} 件のレシピを RecipeManager に登録しました。",
                allRecipes.size());
    }

    private static boolean isGeneratedDisassemblyRecipe(ResourceLocation id) {
        return id.getNamespace().equals(Moveearth_addtional.MODID)
                && id.getPath().startsWith("disassemble_");
    }

    /**
     * 2つの Ingredient が同じアイテムを表しているかを簡易判定する
     */
    private static boolean ingredientItemsMatch(Ingredient a, Ingredient b) {
        var itemsA = a.getItems();
        var itemsB = b.getItems();
        if (itemsA.length == 0 || itemsB.length == 0) return false;
        return itemsA[0].getItem() == itemsB[0].getItem();
    }

    /**
     * GunSmithTableRecipe の RecipeType を取得する。
     * ModRecipe.GUN_SMITH_TABLE_CRAFTING から直接取得する。
     */
    @SuppressWarnings("unchecked")
    private static RecipeType<GunSmithTableRecipe> getGunSmithTableRecipeType() {
        try {
            return (RecipeType<GunSmithTableRecipe>) ModRecipe.GUN_SMITH_TABLE_CRAFTING.get();
        } catch (Exception e) {
            LOGGER.warn("[MoveEarth] GunSmithTableRecipeType の取得に失敗: {}", e.getMessage());
            return null;
        }
    }
}

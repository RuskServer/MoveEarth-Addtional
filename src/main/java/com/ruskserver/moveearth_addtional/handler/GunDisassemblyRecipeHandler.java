package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * サーバー起動直前に TaCZ のガンスミスレシピを動的に読み取り、
 * Create の粉砕ホイールレシピとして自動注入するハンドラー。
 * 銃の素材を 60% 還元し、端数は確率ドロップとして処理する。
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public class GunDisassemblyRecipeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GunDisassemblyRecipeHandler.class);
    private static final double RETURN_RATE = 0.6;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            injectCrushingRecipes(event.getServer().getRecipeManager());
        } catch (Exception e) {
            LOGGER.error("[MoveEarth] 銃解体レシピの動的注入に失敗しました", e);
        }
    }

    private static void injectCrushingRecipes(RecipeManager recipeManager) throws Exception {
        Map<ResourceLocation, GunSmithTableRecipe> gunRecipes = TimelessAPI.getAllRecipes();

        if (gunRecipes == null || gunRecipes.isEmpty()) {
            LOGGER.warn("[MoveEarth] TaCZ の銃レシピが見つかりませんでした。動的解体レシピの生成をスキップします。");
            return;
        }

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();

        for (Map.Entry<ResourceLocation, GunSmithTableRecipe> entry : gunRecipes.entrySet()) {
            ResourceLocation gunId = entry.getKey();
            GunSmithTableRecipe gunRecipe = entry.getValue();

            // 結果が銃アイテムではないレシピ（弾薬・アタッチメントなど）は除外
            if (gunRecipe.getResult() == null || gunRecipe.getResult().getResult() == null
                    || gunRecipe.getResult().getResult().isEmpty()) {
                continue;
            }

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

            // inputs フィールドに銃アイテムの Ingredient をセット
            Field ingredientsField = ProcessingRecipeParams.class.getDeclaredField("ingredients");
            ingredientsField.setAccessible(true);
            NonNullList<Ingredient> ingredientList = NonNullList.create();
            ingredientList.add(Ingredient.of(gunRecipe.getResult().getResult().getItem()));
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
            newRecipes.add(new RecipeHolder<>(recipeId, crushingRecipe));
        }

        if (newRecipes.isEmpty()) {
            LOGGER.warn("[MoveEarth] 生成できた解体レシピが 0 件でした。");
            return;
        }

        // RecipeManager の内部マップに強制的に追記
        injectIntoRecipeManager(recipeManager, newRecipes);
        LOGGER.info("[MoveEarth] TaCZ 銃解体レシピを {} 件動的に注入しました。", newRecipes.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectIntoRecipeManager(RecipeManager recipeManager, List<RecipeHolder<?>> newRecipes) throws Exception {
        // NeoForge 21.1.x の RecipeManager は `byName` (LinkedHashMap) と
        // `byType` (Map<RecipeType, List<RecipeHolder>>) を内部に持つ
        Field byNameField = null;
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            if (f.getType().equals(Map.class) && f.getName().contains("byName") || f.getName().contains("recipes")) {
                byNameField = f;
                break;
            }
        }

        if (byNameField == null) {
            // フィールド名で確実にとる（obfuscated 環境では SRG 名 f_44132_ 等になる場合あり）
            Field[] fields = RecipeManager.class.getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                Object val = f.get(recipeManager);
                if (val instanceof Map) {
                    byNameField = f;
                    break;
                }
            }
        }

        if (byNameField == null) {
            LOGGER.error("[MoveEarth] RecipeManager の内部マップフィールドが見つかりませんでした。");
            return;
        }

        byNameField.setAccessible(true);
        Map<ResourceLocation, RecipeHolder<?>> byName = (Map<ResourceLocation, RecipeHolder<?>>) byNameField.get(recipeManager);

        // 変更可能な Map にラップされている場合は直接追加、不可の場合はコピーして差し替え
        try {
            for (RecipeHolder<?> holder : newRecipes) {
                byName.put(holder.id(), holder);
            }
        } catch (UnsupportedOperationException e) {
            // ImmutableMap の場合はコピーして差し替え
            Map<ResourceLocation, RecipeHolder<?>> mutable = new HashMap<>(byName);
            for (RecipeHolder<?> holder : newRecipes) {
                mutable.put(holder.id(), holder);
            }
            byNameField.set(recipeManager, mutable);
        }
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
}

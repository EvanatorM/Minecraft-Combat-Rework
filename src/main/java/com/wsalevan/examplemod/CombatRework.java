package com.wsalevan.examplemod;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CombatRework.MODID)
public class CombatRework {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "combatrework";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CombatRework(IEventBus modEventBus, ModContainer modContainer) {

    }

    @EventBusSubscriber(modid = CombatRework.MODID)
    public static class CombatReworkEvents {
        public static int getResistanceLevel(LivingEntity entity) {
            var instance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (instance != null) {
                // Amplifier is 0-based, so Level I returns amplifier 0, Level II -> 1, etc.
                return instance.getAmplifier() + 1;
            }
            return 0; // no Resistance effect
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent e) {
            // Get target of attack
            LivingEntity target = e.getEntity();
            // Get base attack damage
            float baseDamage = e.getOriginalAmount();

            // 1) Replace vanilla damage reduction
            e.addReductionModifier(DamageContainer.Reduction.ARMOR, (damageContainer, v) -> 0f);
            e.addReductionModifier(DamageContainer.Reduction.ENCHANTMENTS, (damageContainer, v) -> 0f);
            e.addReductionModifier(DamageContainer.Reduction.MOB_EFFECTS, (damageContainer, v) -> 0f);

            // 2) Use new damage formula
            // Get armor values
            float armor = (float)target.getAttributeValue(Attributes.ARMOR);
            float toughness = (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

            // Get enchantment values
            int protLevels = 0;
            var access   = target.level().registryAccess();
            var enchReg  = access.registryOrThrow(Registries.ENCHANTMENT);        // Registry<Enchantment>
            var protHold = enchReg.getHolderOrThrow(Enchantments.PROTECTION);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

                ItemStack stack = target.getItemBySlot(slot);
                var ench = stack.get(DataComponents.ENCHANTMENTS); // ItemEnchantments in 1.21+
                if (ench == null) continue;

                // vanilla id: "minecraft:protection"
                int lvl = ench.getLevel(protHold);
                protLevels += lvl;
            }

            // Get resistance values
            int resLevel = getResistanceLevel(target);

            LOGGER.info("Base damage: " + baseDamage + ", Armor value: " + armor + ", Toughness: " + toughness + ", Prot levels: " + protLevels + ", Res level: " + resLevel);

            // Apply formula
            float armorMultiplier = 1 - (0.9f * (armor / (armor + 25)));
            float toughnessMultiplier = 0.5f + 0.5f * (float)Math.pow(Math.E, (-baseDamage) / (3000.0 / (toughness + 1.0)));
            float protMultiplier = 1 - (Math.min(protLevels * 0.02f, 0.8f));
            float resMultiplier = 1 - (Math.min(resLevel * 0.2f, 0.8f));

            float result = baseDamage * armorMultiplier * toughnessMultiplier * protMultiplier * resMultiplier;

            LOGGER.info("Base damage: " + baseDamage + ", Armor Multiplier: " +  armorMultiplier + ", Toughness Multiplier: " + toughnessMultiplier + ", Prot Multiplier: " + protMultiplier + ", Res Multiplier: " + resMultiplier + ", Final Damage: " + result);
            e.setAmount(result);
        }
    }
}

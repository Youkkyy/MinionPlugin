package fr.lyna.minion.listeners;

import fr.lyna.minion.MinionPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rend les minions complètement invulnérables.
 * Mise à jour pour 1.21+ (Attribute API).
 */
public class MinionDamageListener implements Listener {

    private final MinionPlugin plugin;
    private final NamespacedKey minionKey;

    public MinionDamageListener(MinionPlugin plugin) {
        this.plugin = plugin;
        this.minionKey = new NamespacedKey(plugin, "minion_uuid");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
            resetHealth((Villager) event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
            resetHealth((Villager) event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
            resetHealth((Villager) event.getEntity());

            // Log de sécurité
            plugin.getLogger().warning("Un minion a failli mourir ! UUID: " +
                    event.getEntity().getPersistentDataContainer().get(minionKey, PersistentDataType.STRING));
        }
    }

    private boolean isMinion(Entity entity) {
        return entity instanceof Villager villager &&
                villager.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING);
    }

    private void resetHealth(Villager villager) {
        // ✅ FIX 1.21.8 : Utilisation de MAX_HEALTH (remplace GENERIC_MAX_HEALTH depuis
        // 1.21.2)
        AttributeInstance attribute = villager.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            villager.setHealth(attribute.getValue());
        }
    }
}
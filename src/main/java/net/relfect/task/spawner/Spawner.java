package net.relfect.task.spawner;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import com.gmail.filoghost.holographicdisplays.object.line.CraftTextLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.relfect.task.RPG;
import net.relfect.task.boss.Boss;
import net.relfect.task.boss.BossType;
import net.relfect.task.spawner.menu.SpawnerMenu;
import net.relfect.task.util.BukkitUtil;
import net.relfect.task.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Spawner implements ConfigurationSerializable {

    String id;
    Boss current;
    BossType type;
    Location spawnLocation;
    long interval, respawnAt;
    SpawnerMenu spawnerMenu;
    Hologram hologram;
    boolean showHologram = true;
    String displayName;
    double damage, health;

    public Spawner(String id, String displayName, Location spawnLocation, BossType type, double health, double damage, Number interval) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.health = health;
        this.damage = damage;
        this.spawnLocation = spawnLocation;
        this.interval = interval.longValue();
        this.spawnerMenu = new SpawnerMenu(this);
    }

    public Spawner(Map<String, Object> map) {
        this((String) map.get("id"), BukkitUtil.color((String) map.get("displayName")), BukkitUtil.fromString((String) map.get("location")), BossType.valueOf((String) map.get("type")), ((Number) map.get("damage")).doubleValue(), ((Number) map.get("health")).doubleValue(), ((Number) map.get("interval")).longValue());
        this.respawnAt = ((Number) map.get("respawnAt")).longValue();
        this.showHologram = (Boolean) map.get("showHologram");
    }

    /*
     * Основной метод для смерти текущего моба:
     * 1. Обновляет время респавна
     * 2. Обновляет текущего босса
     * */
    public void die() {
        respawnAt = System.currentTimeMillis() + interval;
        current = null;
    }

    /*
     * Основной метод для тика спавнера (1 тик = 20 секунд):
     * 1. Тикает босса, если он живой
     * 2. Проверяет, если он готов для спавна
     * 3. Обновляет таймер-голограмму, если он мёртв
     * */
    public void update() {
        if(isAlive()) {
            current.onTick();
        } else if(getRemainingUntilRespawn() <= 0) {
            spawn();
        } else if(showHologram) {
            String time = BukkitUtil.color(displayName + " &fпоявится через &e" + TimeUtil.formatSeconds(getRemainingUntilRespawn() / 1000));
            if(hologram == null) {
                hologram = HologramsAPI.createHologram(RPG.getInstance(), spawnLocation.clone().add(0, 1.5, 0));
                hologram.appendTextLine("Привет, {player}!"); // TODO: Fix {player} somehow...
                hologram.appendTextLine(time);
                return;
            }
            ((CraftTextLine)hologram.getLine(1)).setText(time);
        } else clearHologram();
    }

    /*
     * Основной метод для спавна босса:
     * 1. Очищает таймер-голограмму если она есть
     * 2. Спавнит моба
     * 3. Применяет ХП, имя к мобу
     * */
    public void spawn() {
        current = SpawnerManager.get().spawnBoss(this);
        LivingEntity entity = current.getEntity();
        entity.setCustomName(displayName);
        entity.setMaxHealth(health);
        entity.setHealth(health);
    }

    /*
     * Основной метод для вынужденного удаления моба и голограммы, если они есть
     * */
    public void despawn() {
        if(current != null)
            current.despawn();
        clearHologram();
    }

    /*
     * Очищение таймер-голограммы
     * */
    public void clearHologram() {
        if(hologram != null) {
            hologram.delete();
            hologram = null;
        }
    }

    /*
     * Метод получения описания спавнера в данный момент
     * */
    public List<String> getDescription() {
        return Lists.newArrayList(
                "&7ID: &f" + id,
                "&7Тип: &f" + type.getId(),
                "&7Урон: &f" + damage,
                "&7ХП: &f" + health,
                "&7Статус: " + (isAlive() ? "&a&lЖивой" : "&c&l" + TimeUtil.formatSeconds(getRemainingUntilRespawn() / 1000))
        );
    }

    /*
     * Регистрирует спавнер в системе.
     * */
    public void register() {
        SpawnerManager.get().getSpawners().put(id, this);
    }

    /*
     * Удаляет спавнер в системе
     * Удаляет моба, если он живой
     * Удаляет таймер-голограмму, если она есть
     * */
    public void unregister() {
        despawn();
        SpawnerManager.get().getSpawners().remove(id);
    }

    /*
     * Возвращает true, если моб живой
     * */
    public boolean isAlive() {
        return current != null && !current.getEntity().isDead();
    }

    /*
     * Получение оставшиеся времени до спавна (в миллисекундах)
     * */
    public long getRemainingUntilRespawn() {
        return respawnAt - System.currentTimeMillis();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("id", id);
        map.put("displayName", displayName);
        map.put("health", health);
        map.put("damage", damage);
        map.put("location", BukkitUtil.toString(spawnLocation));
        map.put("type", type.name());
        map.put("interval", interval);
        map.put("showHologram", showHologram);
        map.put("respawnAt", respawnAt);
        return map;
    }
}


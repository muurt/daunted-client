/*
 * Daunted Client - the client for Daunt
 * Copyright (C) 2023  fwanchan and drifter16
 */

package io.github.dauntedclient.client.mod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import org.apache.logging.log4j.*;

import com.google.gson.*;

import io.github.dauntedclient.client.mod.hud.HudElement;
import io.github.dauntedclient.client.mod.impl.*;
import io.github.dauntedclient.client.mod.impl.chunkanimator.ChunkAnimatorMod;
import io.github.dauntedclient.client.mod.impl.cosmetica.CosmeticaMod;
import io.github.dauntedclient.client.mod.impl.discord.DiscordIntegrationMod;
import io.github.dauntedclient.client.mod.impl.hud.*;
import io.github.dauntedclient.client.mod.impl.hud.armour.ArmourMod;
import io.github.dauntedclient.client.mod.impl.hud.chat.ChatMod;
import io.github.dauntedclient.client.mod.impl.hud.crosshair.CrosshairMod;
import io.github.dauntedclient.client.mod.impl.hud.keystrokes.KeystrokesMod;
import io.github.dauntedclient.client.mod.impl.hud.ping.PingMod;
import io.github.dauntedclient.client.mod.impl.hud.tablist.TabListMod;
import io.github.dauntedclient.client.mod.impl.hypixeladditions.HypixelAdditionsMod;
import io.github.dauntedclient.client.mod.impl.itemphysics.ItemPhysicsMod;
import io.github.dauntedclient.client.mod.impl.quickplay.QuickPlayMod;
import io.github.dauntedclient.client.mod.impl.replay.SCReplayMod;
import io.github.dauntedclient.client.mod.impl.togglesprint.ToggleSprintMod;

public final class ModManager implements Iterable<Mod> {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson DEFAULT_GSON = getGson(null);

	private final List<Mod> mods = new LinkedList<>();
	private final Map<String, Mod> byId = new HashMap<>();
	private final List<HudElement> huds = new LinkedList<>();

	// builtin

	/**
	 * Loads the "standard library" of mods. This fails with a log.
	 *
	 * @param storageFile the storage file containing a json map of mod ids to
	 *                    config.
	 */
	public void loadStandard(Path storageFile) {
		LOGGER.info("Loading mods...");

		// @formatter:off
		loadStandard(storageFile,
				// general
				new DauntedClientConfig(),
				new TweaksMod(),

				// hud
				new FpsMod(),
				new CoordinatesMod(),
				new KeystrokesMod(),
				new CpsMod(),
				new PingMod(),
				new ReachDisplayMod(),
				new ComboCounterMod(),
				new ClockMod(),
				new PotionEffectsMod(),
				new ArmourMod(),
				new ChatMod(),
				new TabListMod(),
				new CrosshairMod(),
				new ScoreboardMod(),
				new BossBarMod(),

				// utility
				new SCReplayMod(),
				new FreelookMod(),
				new ToggleSprintMod(),
				new TNTTimerMod(),
				new ZoomMod(),
				new ScrollableTooltipsMod(),
				new ScreenshotsMod(),

				// visual
				new MotionBlurMod(),
				new MenuBlurMod(),
				new ColourSaturationMod(),
				new ChunkAnimatorMod(),
				new V1_7VisualsMod(),
				new ItemPhysicsMod(),
				new ParticlesMod(),
				new TimeChangerMod(),
				new BlockSelectionMod(),
				new HitboxMod(),
				new HitColourMod(),

				// integration
				new CosmeticaMod(),
				new HypixelAdditionsMod(),
				new QuickPlayMod(),
				new DiscordIntegrationMod()
		);
		// @formatter:on

		LOGGER.info("Loaded {} mods", mods.size());
	}

	private void loadStandard(Path storageFile, DauntedClientMod... mods) {
		JsonObject storage = null;

		if (Files.isRegularFile(storageFile)) {
			try (Reader reader = new InputStreamReader(Files.newInputStream(storageFile), StandardCharsets.UTF_8)) {
				storage = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Throwable error) {
				LOGGER.error("Could not load Daunted Client mods storage", error);
			}
		}

		for (Mod mod : mods) {
			JsonObject storageNode = null;
			if (storage != null) {
				JsonElement storageElement = storage.get(mod.getId());
				if (storageElement != null) {
					if (!storageElement.isJsonObject())
						LOGGER.warn("Storage node for {} is not a JsonObject - its type is {}", mod.getId(),
								storageElement.getClass());
					else
						storageNode = storageElement.getAsJsonObject();
				}
			}

			register(mod, storageNode);
		}
	}

	/**
	 * Saves the "standard library" of mods.
	 *
	 * @throws IOException if something went wrong.
	 */
	public void saveStandard(Path storageFile) throws IOException {
		JsonObject result = new JsonObject();

		for (Mod mod : mods)
			if (mod instanceof DauntedClientMod)
				result.add(mod.getId(), save(mod));

		try (Writer out = new OutputStreamWriter(Files.newOutputStream(storageFile))) {
			out.write(result.toString());
		}
	}

	// registration

	/**
	 * Registers a mod. This will make it appear in the menu.
	 *
	 * @param mod the mod to register.
	 */
	public void register(Mod mod, JsonObject config) {
		try {
			// quite broken
			if (Boolean.getBoolean("io.github.dauntedclient.client.mod." + mod.getId() + ".disable"))
				return;

			if (mod instanceof DauntedClientMod)
				((DauntedClientMod) mod).setIndex(mods.size());

			configure(mod, config);
			mod.init();
			mods.add(mod);
			byId.put(mod.getId(), mod);
			huds.addAll(mod.getHudElements());
		} catch (Throwable error) {
			LOGGER.error("Could not register mod {}", mod.getId(), error);

			if (mod instanceof DauntedClientMod)
				((DauntedClientMod) mod).setIndex(-1);
		}
	}

	/**
	 * Loads a JSON configuration into a mod.
	 *
	 * @param mod    the mod.
	 * @param config the configuration object.
	 */
	public void configure(Mod mod, JsonObject config) {
		if (config == null)
			return;

		try {
			getGson(mod).fromJson(config, mod.getClass());
		} catch (Throwable error) {
			LOGGER.error("Could not configure mod {} on {}", mod.getId(), config, error);
		}
	}

	/**
	 * Dumps the config of a mod.
	 *
	 * @param mod the mod.
	 * @return the config as a json object.
	 */
	public JsonObject save(Mod mod) {
		return DEFAULT_GSON.toJsonTree(mod).getAsJsonObject();
	}

	// lookup

	/**
	 * Gets a mod - which may or may not be present - by its id.
	 *
	 * @param id the mod id.
	 * @return an optional mod.
	 */
	public Optional<Mod> getById(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	/**
	 * Gets a mod, or throws if it's not present.
	 *
	 * @param id the mod id.
	 * @return the mod - not <code>null</code>.
	 */
	public Mod getByIdOrThrow(String id) {
		Mod result = byId.get(id);
		if (result == null)
			throw new IllegalArgumentException(id);

		return result;
	}

	/**
	 * Gets the list of mods. Note: for efficiency, this returns the internal one.
	 *
	 * @return the list.
	 */
	public List<Mod> getRegistered() {
		return mods;
	}

	/**
	 * Gets the list of mods' huds. Note: for efficiency, this returns the internal
	 * one.
	 *
	 * @return the list.
	 */
	public List<HudElement> getHuds() {
		return huds;
	}

	// misc

	@Override
	public Iterator<Mod> iterator() {
		return mods.iterator();
	}

	public Stream<Mod> stream() {
		return mods.stream();
	}

	private static Gson getGson(Mod mod) {
		GsonBuilder builder = new GsonBuilder();
		if (mod != null)
			builder.registerTypeAdapter(mod.getClass(), (InstanceCreator<Mod>) (type) -> mod);

		return builder.excludeFieldsWithoutExposeAnnotation().create();
	}

}

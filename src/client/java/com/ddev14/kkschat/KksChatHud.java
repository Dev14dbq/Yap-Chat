package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatAnimationType;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.ChatRule;
import com.ddev14.kkschat.chat.MessageBounds;
import com.ddev14.kkschat.skin.PlayerSkinUpdater;
import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD overlay that shows the latest chat message
 * in a compact box above the hotbar.
 */
public class KksChatHud {

	// Server/plugin message formats from the config (tried before the built-in ones)
	final List<com.ddev14.kkschat.pipeline.ServerPattern> serverPatterns = new ArrayList<>();
	// Same patterns as written in the config, kept to save them back unchanged
	final List<com.ddev14.kkschat.pipeline.ServerPattern.Config> serverPatternConfigs = new ArrayList<>();

	// История сообщений для отображения когда чат открыт
	final List<ChatMessageEntry> messageHistory = new ArrayList<>();
	
	// Значения по умолчанию (ранее были в конфиге)
	int maxHistorySize = 100;
	float backgroundOpacity = 0.3f; // Прозрачность фона (0.0 - 1.0, соответствует 0-100%)
	int displayTimeSeconds = 5;
	int chatPosition = 0;
	boolean antiSpamEnabled = true;
	
	// Прокрутка истории чата
	int scrollOffset = 0;
	private static final int SCROLL_SPEED = 3; // Количество сообщений на прокрутку

	// Флаг включения/выключения мода (фильтр чата - блокировка стандартного чата)
	boolean enabled = true;
	
	// Флаг изменения текста сообщений (true = изменять форматирование, false = показывать как есть)
	boolean modifyMessageText = true;
	
	// Максимальное количество сообщений для отображения без сжатия
	int maxVisibleMessages = 10;
	
	// Правила из конфига (применяются к каждому входящему сообщению)
	final List<ChatRule> rules = new java.util.ArrayList<>();

	// Анимация появления и исчезновения сообщений (независимо)
	ChatAnimationType animationIn  = ChatAnimationType.FADE;
	ChatAnimationType animationOut = ChatAnimationType.FADE;

	// Цвет фона по типу сообщения (имя типа → hex-строка, e.g. "#1A0028")
	public java.util.Map<String, String> bgColors = defaultBgColors();

	static java.util.Map<String, String> defaultBgColors() {
		java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
		m.put("PLAYER_CHAT",   "#000000");
		m.put("WHISPER",       "#000000");
		m.put("JOIN_LEAVE",    "#000000");
		m.put("ACHIEVEMENT",   "#001A04");
		m.put("CHALLENGE",     "#1A0028");
		m.put("ERROR",         "#1A0000");
		m.put("SLEEP",         "#000000");
		m.put("SCREENSHOT",    "#000000");
		m.put("COMMAND_BLOCK", "#000000");
		m.put("SYSTEM",        "#000000");
		return m;
	}

	// Иконки по типу сообщения (Minecraft item ID, например "minecraft:stick")
	String iconSystem        = "minecraft:stick";
	String iconError         = "minecraft:barrier";
	String iconSleep         = "minecraft:red_bed";
	String iconAchievement   = "minecraft:emerald";
	String iconChallenge     = "minecraft:netherite_ingot";
	String iconCommandBlock  = "minecraft:command_block";
	String iconWhisper       = "minecraft:paper";
	String iconJoinLeave     = "minecraft:oak_door";

	public String getIconSystem()       { return iconSystem; }
	public String getIconError()        { return iconError; }
	public String getIconSleep()        { return iconSleep; }
	public String getIconAchievement()  { return iconAchievement; }
	public String getIconChallenge()    { return iconChallenge; }
	public String getIconCommandBlock() { return iconCommandBlock; }
	public String getIconWhisper()      { return iconWhisper; }
	public String getIconJoinLeave()    { return iconJoinLeave; }

	public void setIconSystem(String id)       { this.iconSystem = id;       KksChatConfig.save(this); }
	public void setIconError(String id)        { this.iconError = id;        KksChatConfig.save(this); }
	public void setIconSleep(String id)        { this.iconSleep = id;        KksChatConfig.save(this); }
	public void setIconAchievement(String id)  { this.iconAchievement = id;  KksChatConfig.save(this); }
	public void setIconChallenge(String id)    { this.iconChallenge = id;    KksChatConfig.save(this); }
	public void setIconCommandBlock(String id) { this.iconCommandBlock = id; KksChatConfig.save(this); }
	public void setIconWhisper(String id)      { this.iconWhisper = id;      KksChatConfig.save(this); }
	public void setIconJoinLeave(String id)    { this.iconJoinLeave = id;    KksChatConfig.save(this); }

	// Хранит позиции сообщений для обработки кликов (индекс в истории -> позиция на экране)
	final Map<Integer, MessageBounds> messageBounds = new ConcurrentHashMap<>();
	
	// Хранит состояние развернутости сжатых сообщений
	boolean collapsedMessagesExpanded = false;

	/** Stores a classified message in the history. Called by {@link com.ddev14.kkschat.pipeline.MessageIngest}. */
	public void addMessage(com.ddev14.kkschat.pipeline.ClassifiedMessage msg, Component display,
			PlayerInfo senderInfo, PlayerInfo receiverInfo) {
		ChatMessageEntry candidate = new ChatMessageEntry(display, senderInfo, receiverInfo, msg.type(), msg.senderName());
		candidate.direction = msg.direction();
		candidate.receiverName = msg.receiverName();
		if (candidate.senderUUID == null) candidate.senderUUID = msg.senderId();
		ChatHistoryAppender.addLine(this, candidate);
	}

	/** Read-only copy of the history, oldest first (used by tests and integrations). */
	public List<ChatMessageEntry> getHistorySnapshot() {
		return List.copyOf(messageHistory);
	}

	public List<com.ddev14.kkschat.pipeline.ServerPattern> getServerPatterns() {
		return serverPatterns;
	}

	public void clearChat() {
		messageHistory.clear();
		scrollOffset = 0;
		messageBounds.clear();
	}

	private void updateMissingPlayerInfos(Minecraft minecraft) {
		PlayerSkinUpdater.updateMissingPlayerInfos(minecraft, messageHistory);
	}

	/**
	 * Обрабатывает прокрутку колесиком мыши
	 */
	public void handleMouseScroll(double delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof ChatScreen) {
			// Прокрутка вверх (delta > 0) - показываем более старые сообщения
			// Прокрутка вниз (delta < 0) - показываем более новые сообщения
			if (delta > 0) {
				scrollOffset = Math.min(scrollOffset + SCROLL_SPEED, messageHistory.size() - 1);
			} else if (delta < 0) {
				scrollOffset = Math.max(scrollOffset - SCROLL_SPEED, 0);
			}
		}
	}

	public void onHudRender(GuiGraphicsExtractor guiGraphics) {
		// Показываем чат только если он включен
		// Но сообщения продолжают сохраняться даже когда чат выключен
		if (!enabled) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}

		// Обновляем PlayerInfo для сообщений, у которых скин еще не загрузился
		// Это критично для поддержки SkinsRestorer плагина, так как скины загружаются асинхронно
		updateMissingPlayerInfos(minecraft);

		// Всегда рисуем наш HUD (ранее проверяли конфиг)
		
		// Проверяем, открыт ли чат
		boolean chatOpen = minecraft.screen instanceof ChatScreen;
		
		if (chatOpen) {
			ChatHudSceneRenderer.renderOpenHistory(this, guiGraphics, minecraft);
			if (minecraft.screen != null) {
				double mouseX = minecraft.mouseHandler.xpos();
				double mouseY = minecraft.mouseHandler.ypos();
				renderTooltip(guiGraphics, (int)mouseX, (int)mouseY);
			}
			return;
		}

		ChatHudSceneRenderer.renderClosedOverlay(this, guiGraphics, minecraft);
	}
	
	// Флаг для открытия экрана достижений в следующем тике
	boolean shouldOpenAdvancements = false;
	
	public boolean shouldOpenAdvancements() {
		return shouldOpenAdvancements;
	}
	
	public void setShouldOpenAdvancements(boolean value) {
		shouldOpenAdvancements = value;
	}
	
	/**
	 * Обрабатывает клик по сообщению
	 */
	public boolean handleMessageClick(double mouseX, double mouseY) {
		return ChatHudClickDispatch.handleMessageClick(this, mouseX, mouseY);
	}
	
	// Хранит текущее сообщение под курсором для tooltip
	private ChatMessageEntry hoveredEntry = null;
	
	/**
	 * Обрабатывает наведение мыши для показа tooltip
	 */
	public void handleMouseMove(double mouseX, double mouseY) {
		hoveredEntry = null;
		int mx = (int)mouseX;
		int my = (int)mouseY;
		
		// Проверяем, находится ли курсор над сообщением о достижении
		for (Map.Entry<Integer, MessageBounds> boundsEntry : messageBounds.entrySet()) {
			int index = boundsEntry.getKey();
			MessageBounds bounds = boundsEntry.getValue();
			
			// Пропускаем сжатое сообщение и повторения
			if (index == -1 || index >= 1000) {
				continue;
			}
			
			if (index < 1000 && bounds.contains(mx, my)) {
				int historyIndex = bounds.historyIndex;
				if (historyIndex >= 0 && historyIndex < messageHistory.size()) {
					ChatMessageEntry entry = messageHistory.get(historyIndex);
					if (entry != null && entry.isAchievement() && entry.message != null) {
						hoveredEntry = entry;
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Рендерит tooltip для сообщения о достижении при наведении
	 */
	public void renderTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		// Tooltip будет реализован позже, когда найдем правильный API для GuiGraphicsExtractor
		// Пока фокусируемся на том, чтобы клик открывал экран достижений
	}
	
	/**
	 * Сворачивает развернутые сообщения при закрытии чата
	 */
	public void collapseMessagesOnChatClose() {
		collapsedMessagesExpanded = false;
		// Сворачиваем все развернутые повторяющиеся сообщения
		for (ChatMessageEntry entry : messageHistory) {
			if (entry != null) {
				entry.isExpanded = false;
			}
		}
		messageBounds.clear();
	}
	
	/**
	 * Получить состояние включения/выключения мода
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Получить прозрачность фона (0.0 - 1.0)
	 */
	public float getBackgroundOpacity() {
		return backgroundOpacity;
	}
	
	/**
	 * Установить прозрачность фона (0.0 - 1.0, где 0.0 = полностью прозрачный, 1.0 = полностью непрозрачный)
	 */
	public void setBackgroundOpacity(float opacity) {
		this.backgroundOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
		KksChatConfig.save(this);
	}
	
	/**
	 * Установить состояние включения/выключения мода
	 */
	public void setEnabled(boolean enabled) {
		boolean wasEnabled = this.enabled;
		this.enabled = enabled;
		
		// Если чат был выключен, а теперь включен - очищаем стандартный чат
		// чтобы старые сообщения не отображались там
		if (!wasEnabled && enabled) {
			clearVanillaChat();
		}
		KksChatConfig.save(this);
	}
	
	/**
	 * Очищает стандартный чат Minecraft
	 */
	private void clearVanillaChat() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gui == null || minecraft.gui.getChat() == null) {
			return;
		}
		
		// Очищаем стандартный чат (сообщения уже сохранены в нашем HUD)
		minecraft.gui.getChat().clearMessages(false);
	}
	
	/**
	 * Получить флаг изменения текста сообщений
	 */
	public boolean isModifyMessageText() {
		return modifyMessageText;
	}
	
	/**
	 * Установить флаг изменения текста сообщений
	 * @param modify true = изменять форматирование (добавлять цвета, префиксы и т.д.), false = показывать как есть
	 */
	public void setModifyMessageText(boolean modify) {
		this.modifyMessageText = modify;
		KksChatConfig.save(this);
	}

	public int getDisplayTimeSeconds() { return displayTimeSeconds; }
	public void setDisplayTimeSeconds(int seconds) {
		this.displayTimeSeconds = Math.max(1, Math.min(60, seconds));
		KksChatConfig.save(this);
	}

	public int getMaxVisibleMessages() { return maxVisibleMessages; }
	public void setMaxVisibleMessages(int max) {
		this.maxVisibleMessages = Math.max(1, Math.min(50, max));
		KksChatConfig.save(this);
	}

	public int getMaxHistorySize() { return maxHistorySize; }
	public void setMaxHistorySize(int max) {
		this.maxHistorySize = Math.max(50, Math.min(500, max));
		ChatHistoryAppender.trimToMaxHistory(this);
		KksChatConfig.save(this);
	}

	public int getChatPosition() { return chatPosition; }
	public void setChatPosition(int position) {
		this.chatPosition = Math.max(0, Math.min(5, position));
		KksChatConfig.save(this);
	}

	public ChatAnimationType getAnimationIn()  { return animationIn; }
	public ChatAnimationType getAnimationOut() { return animationOut; }
	public void setAnimationIn(ChatAnimationType type) {
		this.animationIn = type != null ? type : ChatAnimationType.FADE;
		KksChatConfig.save(this);
	}
	public void setAnimationOut(ChatAnimationType type) {
		this.animationOut = type != null ? type : ChatAnimationType.FADE;
		KksChatConfig.save(this);
	}

	public java.util.Map<String, String> getBgColors() { return bgColors; }

	public boolean isAntiSpamEnabled() { return antiSpamEnabled; }
	public void setAntiSpamEnabled(boolean enabled) {
		if (this.antiSpamEnabled == enabled) return;
		this.antiSpamEnabled = enabled;
		if (enabled) {
			ChatHistoryAppender.regroupIdentical(this);
		} else {
			ChatHistoryAppender.explodeGroups(this);
		}
		KksChatConfig.save(this);
	}

	/**
	 * Сбросить все настройки к значениям по умолчанию
	 */
	public void resetSettings() {
		this.enabled = true;
		this.backgroundOpacity = 0.3f;
		this.modifyMessageText = true;
		this.displayTimeSeconds = 5;
		this.maxVisibleMessages = 10;
		this.maxHistorySize = 100;
		this.chatPosition = 0;
		this.antiSpamEnabled = true;
		KksChatConfig.save(this);
	}

	/** Загружает настройки из файла конфига. */
	public void loadConfig() {
		KksChatConfig.load(this);
	}
}


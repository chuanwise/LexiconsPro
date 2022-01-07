package cn.chuanwise.xiaoming.lexicons.pro;

import cn.chuanwise.toolkit.box.Box;
import cn.chuanwise.toolkit.serialize.exception.SerializerException;
import cn.chuanwise.xiaoming.contact.message.Message;
import cn.chuanwise.xiaoming.event.MessageEvent;
import cn.chuanwise.xiaoming.lexicons.pro.configuration.LexiconConfiguration;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconEntry;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconManager;
import cn.chuanwise.xiaoming.lexicons.pro.interactors.LexiconsProInteractors;
import cn.chuanwise.xiaoming.listener.ListenerPriority;
import cn.chuanwise.xiaoming.plugin.JavaPlugin;
import cn.chuanwise.xiaoming.user.GroupXiaomingUser;
import cn.chuanwise.xiaoming.user.XiaomingUser;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Getter
public class LexiconsProPlugin extends JavaPlugin {
    public static LexiconsProPlugin INSTANCE = new LexiconsProPlugin();

    LexiconConfiguration configuration;
    LexiconManager lexiconManager;

    @Override
    public void onLoad() {
        final File dataFolder = getDataFolder();

        // 如果不存在插件数据文件夹
        if (!dataFolder.isDirectory()) {
            final File elderDataFolder = new File(getDataFolder().getParentFile(), "lexicons-pro");

            // 也不存在老版本插件数据，则就当作第一次使用
            if (!elderDataFolder.isDirectory()) {
                dataFolder.mkdirs();
            } else {
                // 把 lexicons-pro 文件夹改名为 LexiconsPro
                elderDataFolder.renameTo(dataFolder);
            }
        }

        try {
            final LexiconConfiguration configuration = loadFileAs(LexiconConfiguration.class, new File(getDataFolder(), "configurations.json"));
        } catch (SerializerException | IOException exception) {
            exception.printStackTrace();
        }

        final LexiconConfiguration configuration =
                loadFileOrSupply(LexiconConfiguration.class,
                        new File(getDataFolder(), "configurations.json"),
                        () -> new LexiconConfiguration());

        this.configuration = loadConfigurationOrSupply(LexiconConfiguration.class, LexiconConfiguration::new);
        lexiconManager = loadFileOrSupply(LexiconManager.class, new File(getDataFolder(), "lexicons.json"), LexiconManager::new);
    }

    @Override
    public void onEnable() {
        xiaomingBot.getInteractorManager().registerInteractors(new LexiconsProInteractors(), this);

        setupListeners();
    }

    protected void setupListeners() {
        xiaomingBot.getEventManager().registerListener(MessageEvent.class, ListenerPriority.NORMAL, false, event -> {
            final Message message = event.getMessage();
            final XiaomingUser user = event.getUser();

            final String serializedMessage;
            if (configuration.isListenOriginalMessage()) {
                serializedMessage = message.serializeOriginalMessage();
            } else {
                serializedMessage = message.serialize();
            }
            final Box<LexiconEntry> entry = Box.empty();

            // 寻找匹配的词条
            do {
                // 先寻找私人词库
                final Optional<LexiconEntry> optionalEntry = lexiconManager.forPersonalEntry(user.getCode(), serializedMessage);
                if (optionalEntry.isPresent()) {
                    entry.set(optionalEntry.get());
                    break;
                }

                // 寻找群聊词库
                if (user instanceof GroupXiaomingUser) {
                    final GroupXiaomingUser groupXiaomingUser = (GroupXiaomingUser) user;

                    // 查找在所有 tag 群的词库
                    for (String tag : groupXiaomingUser.getContact().getTags()) {
                        final Optional<LexiconEntry> groupEntry = lexiconManager.forGroupEntry(tag, serializedMessage);
                        if (groupEntry.isPresent()) {
                            entry.set(groupEntry.get());
                            break;
                        }
                    }
                    if (entry.notNull()) {
                        break;
                    }
                }

                // 寻找全局词库
                lexiconManager.forGlobalEntry(serializedMessage).ifPresent(entry::set);
            } while (false);

            // 没有找到词条，不计入调用
            if (entry.isEmpty()) {
                return;
            } else if (configuration.isEnableInteractPermission()
                    && Objects.nonNull(configuration.getInteractPermission())
                    && !user.hasPermission(configuration.getInteractPermission())) {
                user.sendMessage("你还不能调用词条哦，因为你缺少权限：" + configuration.getInteractPermission());
                return;
            }

            // 判断回复是否为空
            final LexiconEntry lexiconEntry = entry.get();
            final String reply = user.format(lexiconEntry.apply(serializedMessage).orElseThrow(NoSuchElementException::new));

            // 决定到底如何发送
            if (lexiconEntry.isPrivateSend()) {
                xiaomingBot.getContactManager().sendPrivateMessagePossibly(user.getCode(), reply);
            } else {
                user.getContact().sendMessage(reply);
            }
        }, this);
    }
}

package cn.chuanwise.xiaoming.lexicons.pro;

import cn.chuanwise.toolkit.serialize.exception.SerializerException;
import cn.chuanwise.util.ConditionUtil;
import cn.chuanwise.xiaoming.lexicons.pro.configuration.LexiconConfiguration;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconEntry;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconManager;
import cn.chuanwise.xiaoming.lexicons.pro.interactor.LexiconsProInteractors;
import cn.chuanwise.xiaoming.plugin.JavaPlugin;
import cn.chuanwise.xiaoming.user.GroupXiaomingUser;
import cn.chuanwise.xiaoming.user.XiaomingUser;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import cn.chuanwise.toolkit.container.Container;

@Getter
public class LexiconsProPlugin extends JavaPlugin {
    public static LexiconsProPlugin INSTANCE = new LexiconsProPlugin();

    LexiconConfiguration configuration;
    LexiconManager lexiconManager;

    @Override
    public void onLoad() {
        getDataFolder().mkdirs();

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
        registerParameterParsers();
    }

    private void registerParameterParsers() {
        xiaomingBot.getInteractorManager().registerParameterParser(LexiconEntry.class, context -> {
            final String inputValue = context.getInputValue();
            final String parameterName = context.getParameterName();
            final XiaomingUser user = context.getUser();

            switch (parameterName) {
                case "群词条":
                case "群聊词条":
                    final String groupTag;
                    if (Objects.nonNull(context.getArgument("群标签"))) {
                        groupTag = context.getArgument("群标签");
                    } else {
                        ConditionUtil.checkState(user instanceof GroupXiaomingUser, "user is not a instance of GroupXiaomingUser!");
                        groupTag = ((GroupXiaomingUser) user).getGroupCodeString();
                    }

                    final Optional<LexiconEntry> optionalGroupEntry = lexiconManager.forGroupEntry(groupTag, inputValue);
                    if (optionalGroupEntry.isEmpty()) {
                        user.sendError("「" + groupTag + "」群中没有词条「" + inputValue + "」");
                        return null;
                    } else {
                        return Container.of(optionalGroupEntry.get());
                    }
                case "私人词条":
                    final Optional<LexiconEntry> optionalPersonalEntry = lexiconManager.forPersonalEntry(user.getCode(), inputValue);
                    if (optionalPersonalEntry.isEmpty()) {
                        user.sendError("你没有私人词条「" + inputValue + "」哦");
                        return null;
                    } else {
                        return Container.of(optionalPersonalEntry.get());
                    }
                case "全局词条":
                    final Optional<LexiconEntry> optionalGlobalEntry = lexiconManager.forGlobalEntry(inputValue);
                    if (optionalGlobalEntry.isEmpty()) {
                        user.sendError("并没有全局词条「" + inputValue + "」");
                        return null;
                    } else {
                        return Container.of(optionalGlobalEntry.get());
                    }
                default:
                    return Container.empty();
            }
        }, true, this);
    }
}

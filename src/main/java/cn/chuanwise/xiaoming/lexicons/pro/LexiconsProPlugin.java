package cn.chuanwise.xiaoming.lexicons.pro;

import cn.chuanwise.xiaoming.lexicons.pro.configuration.LexiconConfiguration;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconManager;
import cn.chuanwise.xiaoming.lexicons.pro.interactor.LexiconInteractor;
import cn.chuanwise.xiaoming.plugin.XiaomingPluginImpl;
import lombok.Getter;

import java.io.File;

@Getter
public class LexiconsProPlugin extends XiaomingPluginImpl {
    public static LexiconsProPlugin INSTANCE;

    LexiconConfiguration configuration;
    LexiconManager lexiconManager;

    @Override
    public void onLoad() {
        INSTANCE = this;
        getDataFolder().mkdirs();

        configuration = loadConfigurationOrSupplie(LexiconConfiguration.class, LexiconConfiguration::new);
        lexiconManager = loadFileOrSupplie(LexiconManager.class, new File(getDataFolder(), "lexicons.json"), LexiconManager::new);
    }

    @Override
    public void onEnable() {
        getXiaomingBot().getInteractorManager().register(new LexiconInteractor(this), this);
    }
}

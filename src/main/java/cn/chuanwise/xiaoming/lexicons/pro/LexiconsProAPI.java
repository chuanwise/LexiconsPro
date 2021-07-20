package cn.chuanwise.xiaoming.lexicons.pro;

import cn.chuanwise.exception.IllegalOperationException;
import cn.chuanwise.exception.WiseException;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconManager;

import java.util.Objects;

public class LexiconsProAPI {
    final LexiconsProPlugin plugin;
    final LexiconManager manager;

    public LexiconsProAPI(LexiconsProPlugin plugin) throws WiseException {
        this.plugin = plugin;
        if (Objects.isNull(plugin)) {
            throw new IllegalOperationException("plugin is null");
        }
        this.manager = plugin.lexiconManager;
    }

    public LexiconsProAPI() throws WiseException {
        if (Objects.isNull(LexiconsProPlugin.INSTANCE)) {
            throw new IllegalOperationException("plugin not load");
        }
        this.plugin = LexiconsProPlugin.INSTANCE;
        this.manager = plugin.lexiconManager;
    }
}

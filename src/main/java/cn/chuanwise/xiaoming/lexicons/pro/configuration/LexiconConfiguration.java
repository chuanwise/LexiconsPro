package cn.chuanwise.xiaoming.lexicons.pro.configuration;

import cn.chuanwise.toolkit.preservable.AbstractPreservable;
import cn.chuanwise.xiaoming.listener.ListenerPriority;
import lombok.Data;

@Data
public class LexiconConfiguration extends AbstractPreservable {
    boolean enableInteractPermission = false;
    String interactPermission = "lexicons.interact";

    boolean listenOriginalMessage = false;
    ListenerPriority triggerPriority = ListenerPriority.NORMAL;
    boolean listenCancelledMessage = false;
}

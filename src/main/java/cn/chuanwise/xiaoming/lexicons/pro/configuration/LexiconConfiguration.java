package cn.chuanwise.xiaoming.lexicons.pro.configuration;

import cn.chuanwise.toolkit.preservable.file.FilePreservableImpl;
import lombok.Data;

@Data
public class LexiconConfiguration extends FilePreservableImpl {
    int maxIterateTime;
    String interactPermission = "lexicons.interact";
}

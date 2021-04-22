package com.xyhsoft.plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ModerOption {


    public static String filterCommitHistory(String version, List<String> history)  {

        StringBuilder sb = new StringBuilder();
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sb.append("## ["+version+"] - "+sdf.format(now) );
        sb.append("\r\n");
        sb.append("### 增加");

        for (String message : history) {
            if (message.startsWith("feat")) {
                message = message.replaceAll("feat(\\(\\)){0,}:", "* ");
                sb.append("\r\n");
                sb.append(message);

            }
        }
        sb.append("\r\n### 修复");
        for (String message : history) {
            if (message.startsWith("fix")) {
                message = message.replaceAll("fix(\\(\\)){0,}:", "* ");
                sb.append("\r\n");
                sb.append(message);

            }
        }
        return sb.toString();
    }

}

package soys.soysmyloot.spring.entity.vo;

import lombok.Data;
import soys.soysmyloot.spring.entity.MyLootConfigFile;

/**
 * 配置文件清单行 VO（/files 返回）。
 */
@Data
public class MyLootConfigFileVo {

    private String id;
    private String name;
    private String file;
    private String desc;

    public MyLootConfigFileVo() {
    }

    public MyLootConfigFileVo(MyLootConfigFile f) {
        this.id = f.getId();
        this.name = f.getName();
        this.file = f.getFile();
        this.desc = f.getDesc();
    }
}

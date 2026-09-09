package com.co.kc.imchat.plugin.excel;

import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ImExcelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImExcelAutoConfiguration.class));

    @Test
    void providesExcelTemplate() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ExcelTemplate.class));
    }

    @Test
    void backsOffForApplicationExcelTemplate() {
        ExcelTemplate customTemplate = new ExcelTemplate();

        contextRunner.withBean(ExcelTemplate.class, () -> customTemplate)
                .run(context -> assertThat(context.getBean(ExcelTemplate.class))
                        .isSameAs(customTemplate));
    }
}

package br.pucminas.iceibank.infra.seguranca;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class ConfiguracaoDeSeguranca {

    @Bean
    public FilterRegistrationBean<FiltroJwt> filtroJwt(JwtService jwtService, SegurancaProperties propriedades) {
        FilterRegistrationBean<FiltroJwt> registro =
                new FilterRegistrationBean<>(new FiltroJwt(jwtService, propriedades.tokenEntreAgencias()));
        registro.addUrlPatterns("/*");
        registro.setOrder(2);
        return registro;
    }

    /** CORS liberado para o frontend Vite (dev). Em producao, restringir a origem. */
    @Bean
    public FilterRegistrationBean<CorsFilter> filtroCors() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOriginPatterns(List.of("*"));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(fonte));
        registro.setOrder(1);        // antes do filtro JWT: o preflight OPTIONS nao leva token
        return registro;
    }
}

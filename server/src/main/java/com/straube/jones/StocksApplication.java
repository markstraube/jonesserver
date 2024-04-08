package com.straube.jones;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class StocksApplication
    extends
    SpringBootServletInitializer
{
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application)
    {
        System.out.println("##### Stocksserver says hello");
        return application.sources(StocksApplication.class);
    }


    public static void main(String[] args)
    {
        SpringApplication.run(StocksApplication.class, args);
    }
}

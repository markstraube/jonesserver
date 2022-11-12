package com.straube.jones;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication
public class StocksApplication
{

	public static void main(String[] args)
	{
		SpringApplication.run(StocksApplication.class, args);
	}
}

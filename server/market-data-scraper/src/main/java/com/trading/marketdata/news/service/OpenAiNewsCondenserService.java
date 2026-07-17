package com.trading.marketdata.news.service;
import com.fasterxml.jackson.annotation.JsonPropertyDescription; import com.fasterxml.jackson.databind.ObjectMapper; import com.openai.client.OpenAIClient; import com.openai.models.responses.*; import com.trading.marketdata.news.persistence.*;
import org.springframework.beans.factory.ObjectProvider; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import java.util.*;
@Service public class OpenAiNewsCondenserService{
 public static final class Output{ @JsonPropertyDescription("Compact factual overview") public String overview; public List<String> activeNarratives; public List<String> contradictions; public List<String> newlyMaterialFacts; }
 private final ObjectProvider<OpenAIClient> clients; private final PromptResourceLoader prompts; private final ObjectMapper mapper;
 @Value("${news.condensation.model:gpt-5.4-mini}") String model; @Value("${news.condensation.prompt-version:market-news-condenser-v1}") String promptVersion;
 public OpenAiNewsCondenserService(ObjectProvider<OpenAIClient> c,PromptResourceLoader p,ObjectMapper m){clients=c;prompts=p;mapper=m;}
 public String condense(List<NewsStoryEntity> stories){var c=clients.getIfAvailable(); if(c==null)throw new IllegalStateException("OpenAI disabled"); String data=stories.stream().map(s->"ID="+s.getId()+" | "+s.getRepresentativeHeadline()+" | "+s.getDirection()+" | "+s.getEventType()+" | articles="+s.getArticleCount()).reduce("",(a,b)->a+"\n"+b);
 var params=ResponseCreateParams.builder().model(model).input(prompts.load(promptVersion)+"\nStories:"+data).text(Output.class).build(); Output o=c.responses().create(params).output().stream().flatMap(i->i.message().stream()).flatMap(m->m.content().stream()).flatMap(x->x.outputText().stream()).findFirst().orElseThrow(); try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 public String model(){return model;} public String promptVersion(){return promptVersion;}
}
package com.example.adoptions;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootApplication
public class AdoptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }


    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor
                .builder(vectorStore)
                .build();
    }

    @Bean
    PromptChatMemoryAdvisor memoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();
        var mwa = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(mwa)
                .build();
    }
}

// look mom, no Lombok!
record Dog(@Id int id, String name, String owner, String description) {
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

@Controller
@ResponseBody
class AssistantController {

    private final ChatClient ai;

    AssistantController(QuestionAnswerAdvisor questionAnswerAdvisor,
                        DogRepository repository, VectorStore vectorStore,
                        DogAdoptionScheduler scheduler,
                        PromptChatMemoryAdvisor memoryAdvisor, ChatClient.Builder ai) {

        if (false) {
            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s".formatted(
                        dog.id(), dog.name(), dog.description()
                ));
                vectorStore.add(List.of(dogument));
            });
        }


        this.ai = ai
                .defaultSystem("""
                        You are an AI powered assistant to help people adopt a dog from the adoption\s
                        agency named Pooch Palace with locations in Las Vegas, Seoul, Tokyo, Singapore, Paris,\s
                        Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                        will be presented below. If there is no information, then return a polite response suggesting we\s
                        don't have any dogs available.
                        """)
                .defaultTools(scheduler)
                .defaultAdvisors(memoryAdvisor, questionAnswerAdvisor)
                .build();
    }

    @GetMapping("/dogs/{user}/assistant")
    String query(@PathVariable String user, @RequestParam String question) {
        return this.ai
                .prompt(question)
                .call()
                .content();
    }
}

@Component
class DogAdoptionScheduler {


    @Tool(description = "schedule an appointment to pick up or adopt a dog from a Pooch Palace location")
    String schedule(
            @ToolParam(description = "the id fo the dog") int dogId, @ToolParam(description = "the name of the dog") String dogName) {
        var i = Instant
                .now()
                .plus(3, ChronoUnit.DAYS)
                .toString();
        System.out.println("the date is " + i + " for " + dogName + '/' + dogId);
        return i;

    }
}
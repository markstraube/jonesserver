package com.straube.jones.controller;


import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.model.StockFundamentals;
import com.straube.jones.service.FundamentalsService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST Controller für Fundamentaldaten von Aktien. Bietet CRUD-Operationen über HTTP-Endpunkte.
 */
@RestController
@RequestMapping("/api/fundamentals")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Fundamentals API", description = "Manages stock fundamentals data")
public class FundamentalsController
{
    private final FundamentalsService service;

    /**
     * Erstellt einen neuen Controller mit dem Standard-Service.
     */
    public FundamentalsController()
    {
        this.service = new FundamentalsService();
    }


    /**
     * Erstellt einen neuen Controller mit einem benutzerdefinierten Service.
     * 
     * @param service Der zu verwendende Service
     */
    public FundamentalsController(FundamentalsService service)
    {
        this.service = service;
    }


    /**
     * Erstellt neue Fundamentaldaten für eine Aktie. POST /api/fundamentals
     * 
     * @param fundamentals Die zu erstellenden Fundamentaldaten
     * @return ResponseEntity mit den erstellten Daten (201) oder Fehler (400, 409, 500)
     */
    @PostMapping
    public ResponseEntity< ? > create(@RequestBody
    StockFundamentals fundamentals)
    {
        try
        {
            StockFundamentals created = service.create(fundamentals);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }
        catch (IllegalArgumentException e)
        {
            if (e.getMessage().contains("existieren bereits"))
            { return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage())); }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
        catch (IOException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ErrorResponse("Fehler beim Speichern der Daten: "
                                                 + e.getMessage()));
        }
    }


    /**
     * Findet Fundamentaldaten für eine bestimmte ISIN. GET /api/fundamentals/{isin}
     * 
     * @param isin Die ISIN der Aktie
     * @return ResponseEntity mit den Fundamentaldaten (200) oder Not Found (404)
     */
    @GetMapping("/{isin}")
    public ResponseEntity< ? > getByIsin(@PathVariable
    String isin)
    {
        try
        {
            Optional<StockFundamentals> fundamentals = service.findByIsin(isin);

            if (fundamentals.isPresent())
            {
                return ResponseEntity.ok(fundamentals.get());
            }
            else
            {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(new ErrorResponse("Keine Fundamentaldaten für ISIN " + isin
                                                     + " gefunden"));
            }
        }
        catch (IOException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ErrorResponse("Fehler beim Laden der Daten: " + e.getMessage()));
        }
    }


    /**
     * Aktualisiert Fundamentaldaten für eine bestimmte ISIN. PUT /api/fundamentals/{isin}
     * 
     * @param isin Die ISIN der Aktie
     * @param fundamentals Die aktualisierten Fundamentaldaten
     * @return ResponseEntity mit den aktualisierten Daten (200) oder Fehler (400, 404, 500)
     */
    @PutMapping("/{isin}")
    public ResponseEntity< ? > update(@PathVariable
    String isin, @RequestBody
    StockFundamentals fundamentals)
    {
        try
        {
            StockFundamentals updated = service.upsert(fundamentals);
            return ResponseEntity.ok(updated);
        }
        catch (IllegalArgumentException e)
        {
            if (e.getMessage().contains("existieren nicht"))
            { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage())); }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
        catch (IOException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ErrorResponse("Fehler beim Aktualisieren der Daten: "
                                                 + e.getMessage()));
        }
    }


    /**
     * Löscht Fundamentaldaten für eine bestimmte ISIN. DELETE /api/fundamentals/{isin}
     * 
     * @param isin Die ISIN der Aktie
     * @return ResponseEntity mit No Content (204) oder Not Found (404)
     */
    @DeleteMapping("/{isin}")
    public ResponseEntity< ? > delete(@PathVariable
    String isin)
    {
        try
        {
            boolean deleted = service.delete(isin);

            if (deleted)
            {
                return ResponseEntity.noContent().build();
            }
            else
            {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(new ErrorResponse("Keine Fundamentaldaten für ISIN " + isin
                                                     + " gefunden"));
            }
        }
        catch (IOException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ErrorResponse("Fehler beim Löschen der Daten: " + e.getMessage()));
        }
    }


    /**
     * Prüft, ob Fundamentaldaten für eine ISIN existieren. GET /api/fundamentals/{isin}/exists
     * 
     * @param isin Die ISIN der Aktie
     * @return ResponseEntity mit boolean (200)
     */
    @GetMapping("/{isin}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable
    String isin)
    {
        boolean exists = service.exists(isin);
        return ResponseEntity.ok(exists);
    }

    /**
     * Hilfsklasse für Fehlermeldungen.
     */
    public static class ErrorResponse
    {
        private String message;

        public ErrorResponse(String message)
        {
            this.message = message;
        }


        public String getMessage()
        {
            return message;
        }


        public void setMessage(String message)
        {
            this.message = message;
        }
    }
}

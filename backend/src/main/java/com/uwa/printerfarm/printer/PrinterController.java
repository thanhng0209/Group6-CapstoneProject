package com.uwa.printerfarm.printer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints for physical 3D printers inventory,
 * operational status, and loaded filament/colours.
 */
@RestController
@RequestMapping("/api/printers")
@Tag(name = "Printers", description = "Endpoints for viewing physical 3D printers and their current status")
public class PrinterController {

    private final PrinterRepository printerRepository;

    public PrinterController(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    @Operation(summary = "Get all printers", description = "Returns a list of all printers and their current status, loaded filament, and colors.")
    @GetMapping
    public ResponseEntity<List<Printer>> getAllPrinters() {
        List<Printer> printers = printerRepository.findAll();
        return ResponseEntity.ok(printers);
    }
}

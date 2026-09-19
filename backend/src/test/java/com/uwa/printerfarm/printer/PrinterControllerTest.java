package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrinterController.class)
@WithMockUser(username = "22345678", roles = "STUDENT")
class PrinterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrinterRepository printerRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void getAllPrintersReturnsListOfPrintersWithStatusAndMaterialAndColour() throws Exception {
        Printer printer1 = new Printer("PRUSA_XL_1", "Prusa XL #1", "PRUSA_XL", "IDLE", "PLA", "Prusa Orange");
        Printer printer2 = new Printer("PRUSA_MK4S_1", "Prusa MK4S #1", "PRUSA_MK4S", "PRINTING", "PETG", "Galaxy Black");

        when(printerRepository.findAll()).thenReturn(List.of(printer1, printer2));

        mockMvc.perform(get("/api/printers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("PRUSA_XL_1"))
                .andExpect(jsonPath("$[0].name").value("Prusa XL #1"))
                .andExpect(jsonPath("$[0].model").value("PRUSA_XL"))
                .andExpect(jsonPath("$[0].status").value("IDLE"))
                .andExpect(jsonPath("$[0].currentMaterial").value("PLA"))
                .andExpect(jsonPath("$[0].loadedFilament").value("PLA"))
                .andExpect(jsonPath("$[0].currentColour").value("Prusa Orange"))
                .andExpect(jsonPath("$[0].color").value("Prusa Orange"))
                .andExpect(jsonPath("$[1].id").value("PRUSA_MK4S_1"))
                .andExpect(jsonPath("$[1].status").value("PRINTING"))
                .andExpect(jsonPath("$[1].loadedFilament").value("PETG"))
                .andExpect(jsonPath("$[1].color").value("Galaxy Black"));
    }

    @Test
    void getAllPrintersReturnsEmptyListWhenNoPrinters() throws Exception {
        when(printerRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/printers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}

package com.uwa.printerfarm.printer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PrinterRepositoryTest {

    @Autowired
    private PrinterRepository printerRepository;

    @Test
    void saveAndFindPrinterByIdAndModel() {
        Printer printer = new Printer("TEST_PRINTER_1", "Test Printer", "PRUSA_XL", "IDLE", "PLA", "Orange");
        printerRepository.save(printer);

        assertThat(printerRepository.findById("TEST_PRINTER_1")).contains(printer);
        assertThat(printerRepository.findByModel("PRUSA_XL")).contains(printer);
        assertThat(printerRepository.findByStatus("IDLE")).contains(printer);
    }
}

package com.fesc.tiendaOnline.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.service.WompiWebhookService;

@RestController
@RequestMapping("/pagos/wompi")
public class WompiWebhookController {

    private final WompiWebhookService wompiWebhookService;

    public WompiWebhookController(WompiWebhookService wompiWebhookService) {
        this.wompiWebhookService = wompiWebhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> recibirWebhook(
            @RequestHeader("x-event-checksum") String checksum,
            @RequestBody String payload) {

        wompiWebhookService.procesarEvento(payload, checksum);
        return ResponseEntity.ok().build();
    }
}

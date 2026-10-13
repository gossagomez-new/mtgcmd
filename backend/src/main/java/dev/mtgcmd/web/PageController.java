package dev.mtgcmd.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
  @GetMapping("/room/{id}")
  public String room() {
    return "forward:/index.html";
  }
}

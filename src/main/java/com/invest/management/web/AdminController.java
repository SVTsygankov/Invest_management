package com.invest.management.web;

import com.invest.management.moex.MoexDataLoader;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondDataLoader;
import com.invest.management.moex.bond.BondRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final MoexDataLoader moexDataLoader;
    private final MoexStockRepository moexStockRepository;
    private final BondDataLoader bondDataLoader;
    private final BondRepository bondRepository;

    public AdminController(MoexDataLoader moexDataLoader,
                           MoexStockRepository moexStockRepository,
                           BondDataLoader bondDataLoader,
                           BondRepository bondRepository) {
        this.moexDataLoader = moexDataLoader;
        this.moexStockRepository = moexStockRepository;
        this.bondDataLoader = bondDataLoader;
        this.bondRepository = bondRepository;
    }

    @GetMapping
    public String adminDashboard(Model model) {
        model.addAttribute("stockCount", moexStockRepository.count());
        model.addAttribute("bondCount", bondRepository.count());
        return "admin-dashboard";
    }

    @PostMapping("/load/stocks")
    public String loadStocks(RedirectAttributes redirectAttributes) {
        try {
            moexDataLoader.loadStocksListA();
            redirectAttributes.addFlashAttribute("successMessage", "Акции списка A обновлены.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось загрузить акции: " + ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/load/bonds")
    public String loadBonds(RedirectAttributes redirectAttributes) {
        try {
            bondDataLoader.loadBonds();
            redirectAttributes.addFlashAttribute("successMessage", "Облигации обновлены.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось загрузить облигации: " + ex.getMessage());
        }
        return "redirect:/admin";
    }
}


package com.homemedguard.service.stock.web;

import com.homemedguard.common.access.FamilyAccessGuard;
import com.homemedguard.common.audit.AuditLog;
import com.homemedguard.common.api.Result;
import com.homemedguard.service.auth.core.SessionUser;
import com.homemedguard.service.stock.DisposalService;
import com.homemedguard.service.stock.DisposalService.DisposeCommand;
import com.homemedguard.service.stock.FamilyDrugService;
import com.homemedguard.service.stock.FamilyDrugService.RegisterCommand;
import com.homemedguard.service.stock.StockBatchService;
import com.homemedguard.service.stock.StockBatchService.IntakeCommand;
import com.homemedguard.service.stock.StockModule;
import com.homemedguard.service.stock.StorageLocationService;
import com.homemedguard.service.stock.api.StockQueryFacade;
import com.homemedguard.service.stock.api.StockQueryFacade.BatchView;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.entity.StorageLocationEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 药箱库存入口（M2）。日期参数一律 yyyy-MM-dd，服务端按 Asia/Shanghai 判定，
 * 前端只传日期不做时区运算（handoff §四.6）。
 * familyId 只表示"想操作哪个家庭"，最终取值来自 {@link FamilyAccessGuard}，不直接采信请求体。
 */
@Tag(name = "05-药箱库存")
@StockModule
@RestController
@Validated
@RequestMapping("/api/stock")
public class StockController {

    public record DrugForm(Long familyId,
                           Long drugDictId,
                           @NotBlank String genericName,
                           String tradeName,
                           String dosageForm,
                           String specification,
                           String approvalNo,
                           String otcRx,
                           String unit,
                           Integer minSafeQty) {

        RegisterCommand toCommand(Long resolvedFamilyId) {
            return new RegisterCommand(resolvedFamilyId, drugDictId, genericName, tradeName, dosageForm,
                    specification, approvalNo, otcRx, unit, minSafeQty);
        }
    }

    public record BatchForm(Long familyId,
                            @NotNull Long familyDrugId,
                            Long locationId,
                            String batchNo,
                            @NotNull LocalDate expiryDate,
                            LocalDate openedDate,
                            @NotNull @Min(1) Integer quantity,
                            BigDecimal purchasePrice,
                            LocalDate purchaseDate) {

        IntakeCommand toCommand(Long resolvedFamilyId) {
            return new IntakeCommand(resolvedFamilyId, familyDrugId, locationId, batchNo, expiryDate,
                    openedDate, quantity, purchasePrice, purchaseDate);
        }
    }

    public record OpenForm(@NotNull LocalDate openedDate) {
    }

    public record LocationForm(Long familyId,
                               Long id,
                               Long parentId,
                               @NotBlank String name,
                               Integer sortNo) {

        StorageLocationService.SaveCommand toCommand(Long resolvedFamilyId) {
            return new StorageLocationService.SaveCommand(id, resolvedFamilyId, parentId, name, sortNo);
        }
    }

    public record LocationView(Long id, Long familyId, Long parentId, String name, Integer sortNo) {

        static LocationView of(StorageLocationEntity location) {
            return new LocationView(location.getId(), location.getFamilyId(), location.getParentId(),
                    location.getName(), location.getSortNo());
        }
    }

    public record ConsumeForm(Long familyId,
                              @NotNull Long familyDrugId,
                              @NotNull @Min(1) Integer quantity,
                              LocalDate asOf) {
    }

    public record DisposeForm(Long familyId,
                              @NotNull Long batchId,
                              @NotBlank String action,
                              String reason,
                              BigDecimal amount) {

        DisposeCommand toCommand(Long resolvedFamilyId, Long operatorUserId) {
            return new DisposeCommand(resolvedFamilyId, batchId, action, reason, amount, operatorUserId);
        }
    }

    /** 出库结果：实际从哪些批次各出了多少，前端据此显示"先吃了哪批"。 */
    public record ConsumeResult(List<Line> lines, int total) {
        public record Line(Long batchId, String batchNo, LocalDate useByDate, int quantity) {
        }
    }

    /** 在册药回显：只给前端要用的列，不外泄 created_at 之类的底座字段。 */
    public record DrugEcho(Long id,
                           Long familyId,
                           Long drugDictId,
                           String source,
                           String genericName,
                           String dosageForm,
                           String specification,
                           String unit) {

        static DrugEcho of(FamilyDrugEntity drug) {
            return new DrugEcho(drug.getId(), drug.getFamilyId(), drug.getDrugDictId(), drug.getSource(),
                    drug.getGenericName(), drug.getDosageForm(), drug.getSpecification(), drug.getUnit());
        }
    }

    private final FamilyDrugService familyDrugs;
    private final StockBatchService batches;
    private final StockQueryFacade stockQuery;
    private final DisposalService disposals;
    private final StorageLocationService locationService;
    private final FamilyAccessGuard access;

    public StockController(FamilyDrugService familyDrugs,
                           StockBatchService batches,
                           StockQueryFacade stockQuery,
                           DisposalService disposals,
                           StorageLocationService locationService,
                           FamilyAccessGuard access) {
        this.familyDrugs = familyDrugs;
        this.batches = batches;
        this.stockQuery = stockQuery;
        this.disposals = disposals;
        this.locationService = locationService;
        this.access = access;
    }

    @Operation(summary = "登记家庭在册药：字典查得到就以字典为准，查不到退回手工快照")
    @AuditLog(action = "DRUG_REGISTER", targetType = "family_drug")
    @PostMapping("/drugs")
    public Result<DrugEcho> register(@Valid @RequestBody DrugForm form) {
        Long familyId = access.requireFamilyId(form.familyId());
        return Result.ok(DrugEcho.of(familyDrugs.register(form.toCommand(familyId))));
    }

    @Operation(summary = "批次入库，同药同批号同效期重复提交返回首次那条")
    @AuditLog(action = "BATCH_INTAKE", targetType = "stock_batch")
    @PostMapping("/batches")
    public Result<BatchView> intake(@Valid @RequestBody BatchForm form) {
        Long familyId = access.requireFamilyId(form.familyId());
        return Result.ok(stockQuery.batch(batches.intake(form.toCommand(familyId)).getId()).orElseThrow());
    }

    @Operation(summary = "开封：按剂型开封后使用期重算最终日")
    @AuditLog(action = "BATCH_OPEN", targetType = "stock_batch")
    @PostMapping("/batches/{id}/open")
    public Result<BatchView> open(@PathVariable Long id, @Valid @RequestBody OpenForm form) {
        // 批次属于哪个家庭由库存服务按 family_drug 反查校验，这里只保证调用方已登录
        return Result.ok(stockQuery.batch(batches.markOpened(id, form.openedDate()).getId()).orElseThrow());
    }

    @Operation(summary = "FEFO 出库：先到期先出，数量不足整笔失败")
    @AuditLog(action = "BATCH_CONSUME", targetType = "stock_batch")
    @PostMapping("/consume")
    public Result<ConsumeResult> consume(@Valid @RequestBody ConsumeForm form) {
        Long familyId = access.requireFamilyId(form.familyId());
        LocalDate asOf = form.asOf() == null ? LocalDate.now() : form.asOf();
        var plan = batches.consume(familyId, form.familyDrugId(), form.quantity(), asOf);
        List<ConsumeResult.Line> lines = plan.stream()
                .map(allocation -> stockQuery.batch(allocation.batchId())
                        .map(view -> new ConsumeResult.Line(allocation.batchId(), view.batchNo(),
                                allocation.useByDate(), allocation.quantity()))
                        .orElse(new ConsumeResult.Line(allocation.batchId(), null,
                                allocation.useByDate(), allocation.quantity())))
                .toList();
        int total = plan.stream().mapToInt(allocation -> allocation.quantity()).sum();
        return Result.ok(new ConsumeResult(lines, total));
    }

    @Operation(summary = "处置并记台账：丢弃/交回按购入价折算浪费，效期更正不算损失")
    @AuditLog(action = "BATCH_DISPOSE", targetType = "disposal_record")
    @PostMapping("/dispose")
    public Result<Void> dispose(@Valid @RequestBody DisposeForm form) {
        Long familyId = access.requireFamilyId(form.familyId());
        disposals.dispose(form.toCommand(familyId, SessionUser.requireId()));
        return Result.ok();
    }

    @Operation(summary = "某在册药的可用批次，按效期升序（FEFO 消费顺序）")
    @GetMapping("/batches")
    public Result<List<BatchView>> list(@NotNull @RequestParam Long familyDrugId,
                                        @RequestParam(required = false) Long familyId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        Long mine = access.requireFamilyId(familyId);
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        return Result.ok(stockQuery.availableBatches(familyDrugId, date).stream()
                .filter(view -> mine.equals(view.familyId()))
                .toList());
    }

    @Operation(summary = "药箱分层位置列表（柜→层→格），平铺返回由前端组树")
    @GetMapping("/locations")
    public Result<List<LocationView>> locations(@RequestParam(required = false) Long familyId) {
        Long mine = access.requireFamilyId(familyId);
        return Result.ok(locationService.list(mine).stream().map(LocationView::of).toList());
    }

    @Operation(summary = "新增或改名一个存放位置；parentId 为空即根节点")
    @AuditLog(action = "LOCATION_SAVE", targetType = "storage_location")
    @PostMapping("/locations")
    public Result<LocationView> saveLocation(@Valid @RequestBody LocationForm form) {
        Long mine = access.requireFamilyId(form.familyId());
        return Result.ok(LocationView.of(
                locationService.save(form.toCommand(mine))));
    }

    @Operation(summary = "删除存放位置：下面还有子格子或停着批次时会被拒绝")
    @AuditLog(action = "LOCATION_DELETE", targetType = "storage_location")
    @DeleteMapping("/locations/{id}")
    public Result<Void> deleteLocation(@PathVariable Long id,
                                       @RequestParam(required = false) Long familyId) {
        locationService.delete(access.requireFamilyId(familyId), id);
        return Result.ok();
    }
}

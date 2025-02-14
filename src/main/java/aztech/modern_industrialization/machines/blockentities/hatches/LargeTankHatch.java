/*
 * MIT License
 *
 * Copyright (c) 2020 Azercoco & Technici4n
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package aztech.modern_industrialization.machines.blockentities.hatches;

import aztech.modern_industrialization.MIText;
import aztech.modern_industrialization.compat.waila.holder.FluidStorageComponentHolder;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.blockentities.multiblocks.LargeTankMultiblockBlockEntity;
import aztech.modern_industrialization.machines.components.FluidStorageComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

public class LargeTankHatch extends HatchBlockEntity implements FluidStorageComponentHolder {
    private final MIInventory inventory = new MIInventory(List.of(), List.of(), SlotPositions.empty(), SlotPositions.empty());

    @Nullable
    private LargeTankMultiblockBlockEntity controller = null;

    public LargeTankHatch(BEP bep) {
        super(bep, new MachineGuiParameters.Builder("large_tank_hatch", false).build(), new OrientationComponent.Params(false, false, false));
    }

    @Override
    public MIInventory getInventory() {
        return inventory;
    }

    @Override
    public void openMenu(Player player) {
        if (controller != null) {
            controller.openMenu(player);
        } else {
            player.displayClientMessage(MIText.NoLargeTank.text().withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public HatchType getHatchType() {
        return HatchType.LARGE_TANK;
    }

    @Override
    public boolean upgradesToSteel() {
        return false;
    }

    @Override
    public void unlink() {
        super.unlink();
        controller = null;
    }

    @Override
    public @Nullable FluidStorageComponent getFluidStorageComponent() {
        return controller == null ? null : controller.getFluidStorageComponent();
    }

    public void setController(LargeTankMultiblockBlockEntity controller) {
        this.controller = controller;
    }

    private Storage<FluidVariant> getStorage() {
        return controller == null ? Storage.empty() : controller.getStorage();
    }

    public static void registerFluidApi(BlockEntityType<?> bet) {
        FluidStorage.SIDED.registerForBlockEntity((be, direction) -> ((LargeTankHatch) be).getStorage(), bet);
    }
}

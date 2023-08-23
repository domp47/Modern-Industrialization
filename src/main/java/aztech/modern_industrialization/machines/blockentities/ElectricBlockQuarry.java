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
package aztech.modern_industrialization.machines.blockentities;

import aztech.modern_industrialization.ModernIndustrialization;
import aztech.modern_industrialization.api.energy.CableTier;
import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.api.energy.MIEnergyStorage;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.IComponent;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.components.EnergyComponent;
import aztech.modern_industrialization.machines.components.IsActiveComponent;
import aztech.modern_industrialization.machines.components.MachineInventoryComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.guicomponents.AutoExtract;
import aztech.modern_industrialization.machines.guicomponents.EnergyBar;
import aztech.modern_industrialization.machines.guicomponents.ProgressBar;
import aztech.modern_industrialization.machines.models.MachineModelClientData;
import aztech.modern_industrialization.util.Simulation;
import aztech.modern_industrialization.util.Tickable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class ElectricBlockQuarry extends MachineBlockEntity implements Tickable {
    protected static final int OUTPUT_SLOT_X = 110;
    protected static final int OUTPUT_SLOT_Y = 30;
    private static final ProgressBar.Parameters PROGRESS_BAR = new ProgressBar.Parameters(79, 29, "extract");
    private static final int OPERATION_TICKS = 30;
    private static final int EU_USAGE = 128;
    protected int operatingTicks = 0; // number of ticks spent pumping this iteration
    protected IsActiveComponent isActiveComponent;
    private final MachineInventoryComponent inventoryComponent;
    private final EnergyComponent energy;
    private final MIEnergyStorage insertable;
    private final Block REPLACEMENT_BLOCK = Blocks.AIR;

    private int quarryChunks = 1;
    private BlockPos blockToMine = null;
    private boolean miningComplete = false;

    // TODO - item pipes don't work????????????
    public ElectricBlockQuarry(BEP bep) {
        super(bep, new MachineGuiParameters.Builder("electric_block_quarry", false).build(), new OrientationComponent.Params(true, true, false));

        isActiveComponent = new IsActiveComponent();
        registerGuiComponent(new ProgressBar.Server(PROGRESS_BAR, () -> (float) this.operatingTicks / (OPERATION_TICKS * EU_USAGE)));

        this.registerComponents(isActiveComponent, new IComponent() {
            @Override
            public void writeNbt(CompoundTag tag) {
                tag.putInt("operatingTicks", operatingTicks);
                tag.putInt("quarryChunks", quarryChunks);
                tag.putBoolean("miningComplete", miningComplete);

                if (blockToMine != null) {
                    tag.putLong("blockToMine", blockToMine.asLong());
                }
            }

            @Override
            public void readNbt(CompoundTag tag) {
                operatingTicks = tag.getInt("operatingTicks");
                quarryChunks = tag.getInt("quarryChunks");
                miningComplete = tag.getBoolean("miningComplete");

                if (tag.contains("blockToMine")) {
                    blockToMine = BlockPos.of(tag.getLong("blockToMine"));
                }
            }
        });

        List<ConfigurableItemStack> itemOutputStacks = new ArrayList<>();
        for (int i = 0; i < 4; ++i) {
            itemOutputStacks.add(ConfigurableItemStack.standardOutputSlot());
        }
        SlotPositions itemOutputPositions = new SlotPositions.Builder().addSlots(OUTPUT_SLOT_X, OUTPUT_SLOT_Y, 2, 2).build();
        this.inventoryComponent = new MachineInventoryComponent(Collections.emptyList(), itemOutputStacks, Collections.emptyList(),
                Collections.emptyList(), itemOutputPositions, SlotPositions.empty());

        this.energy = new EnergyComponent(this, 3200);
        this.insertable = energy.buildInsertable(tier -> tier == CableTier.HV);
        registerGuiComponent(new EnergyBar.Server(new EnergyBar.Parameters(18, 32), energy::getEu, energy::getCapacity));
        this.registerComponents(energy);
        this.registerComponents(inventoryComponent);

        registerGuiComponent(new AutoExtract.Server(orientation, false));
    }

    protected long consumeEu(long max) {
        return energy.consumeEu(max, Simulation.ACT);
    }

    public MIInventory getInventory() {
        return inventoryComponent.inventory;
    }

    protected MachineModelClientData getModelData() {
        MachineModelClientData data = new MachineModelClientData();
        data.isActive = isActiveComponent.isActive;
        orientation.writeModelData(data);
        return data;
    }

    public EnergyComponent getEnergyComponent() {
        return energy;
    }

    public static void registerEnergyApi(BlockEntityType<?> bet) {
        EnergyApi.SIDED.registerForBlockEntities((be, direction) -> ((ElectricBlockQuarry) be).insertable, bet);
    }

    private void updateActive(boolean newActive) {
        isActiveComponent.updateActive(newActive, this);
    }

    /**
     * @return The corner of the quarry with the lowest x-axis & y-axis
     */
    private BlockPos getStartingBlockPos() {
        BlockPos behindQuarry = worldPosition.relative(orientation.facingDirection, -1);

        int shortEdge = ((quarryChunks / 2) * 16) + 7;
        int longEdge = ((quarryChunks - 1) * 16) + 15;

        Vec3i quarryDelta = behindQuarry.subtract(worldPosition);
        Vec3i startingDelta;
        // Mine n number chunks to the north
        if (quarryDelta.getZ() == 1) {
            startingDelta = new Vec3i(-shortEdge, 0, 0);
        }
        // Mine n number of chunks to the south
        else if (quarryDelta.getZ() == -1) {
            startingDelta = new Vec3i(-shortEdge, 0, longEdge);
        }
        // Mine n number of chunks to the east
        else if (quarryDelta.getX() == 1) {
            startingDelta = new Vec3i(0, 0, shortEdge);
        }
        // Mine n number of chunks to the west
        else {
            startingDelta = new Vec3i(longEdge, 0, shortEdge);
        }

        return behindQuarry.offset(startingDelta).below();
    }

    private BlockPos calculateNextBlockToMine() {
        // This is the first block being mined. Calculate it.
        if (miningComplete) {
            return null;
        }

        BlockPos startingPos = getStartingBlockPos();

        if (blockToMine == null) {
            return startingPos;
        }

        int minX = startingPos.getX();
        int maxX = minX + ((quarryChunks - 1) * 16) + 15;

        int minZ = startingPos.getZ();
        int maxZ = minZ + ((quarryChunks - 1) * 16) + 15;

        for (int y = blockToMine.getY(); y > -64; y--) {
            for (int z = blockToMine.getZ(); z <= maxZ; z++) {
                for (int x = blockToMine.getX() + 1; x <= maxX; x++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(blockPos).getBlock();
                    FluidState fluidState = level.getFluidState(blockPos);

                    if (block.equals(Blocks.AIR) || block.equals(Blocks.BEDROCK) || !fluidState.is(Fluids.EMPTY)) {
                        continue;
                    }

                    return blockPos;
                }
            }
        }

        miningComplete = true;
        return null;
    }

    private ConfigurableItemStack getResultSlot(List<ConfigurableItemStack> itemStacks, Block targetBlock) {
        ConfigurableItemStack stackToAddTo = null;
        ConfigurableItemStack emptyStack = null;
        for (ConfigurableItemStack itemStack : itemStacks) {
            if (emptyStack == null && itemStack.isEmpty()) {
                emptyStack = itemStack;
            } else if (itemStack.getResource().getItem().equals(targetBlock.asItem()) && itemStack.getAmount() < itemStack.getCapacity()) {
                stackToAddTo = itemStack;
                break;
            }
        }

        return stackToAddTo == null ? emptyStack : stackToAddTo;
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            long eu = consumeEu(EU_USAGE);
            updateActive(eu > 0);
            operatingTicks += eu;

            int totalEU = OPERATION_TICKS * EU_USAGE;
            int completion = operatingTicks / totalEU;
            operatingTicks = operatingTicks % totalEU;

            if (completion == 1) {
                blockToMine = calculateNextBlockToMine();
                ModernIndustrialization.LOGGER.info("Mining Block...");
                ModernIndustrialization.LOGGER.info(blockToMine);

                if (miningComplete) {
                    updateActive(false);
                    getInventory().autoExtractItems(level, worldPosition, orientation.outputDirection);
                    setChanged();
                    return;
                }

                if (blockToMine != null) {
                    level.setBlock(blockToMine, REPLACEMENT_BLOCK.defaultBlockState(), 2 + 4);
                }
            }
        }
    }

    // @Override
    public void tick_final() {
        if (!level.isClientSide) {
            BlockPos posToMine = blockToMine;
            if (miningComplete) {
                updateActive(false);
                getInventory().autoExtractItems(level, worldPosition, orientation.outputDirection);
                setChanged();
                return;
            }

            // This is the first time we're calling this, so generate first position to mine
            if (posToMine == null) {
                posToMine = calculateNextBlockToMine();
            }

            // TODO maybe get contents of chest if I feel like it?
            Block nextBlockToMine = level.getBlockState(posToMine).getBlock();

            List<ConfigurableItemStack> itemStacks = this.getInventory().getItemStacks();
            ConfigurableItemStack slotToInsert = getResultSlot(itemStacks, nextBlockToMine);

            if (slotToInsert == null) {
                updateActive(false);
                getInventory().autoExtractItems(level, worldPosition, orientation.outputDirection);
                setChanged();
                return;
            }

            long eu = consumeEu(EU_USAGE);
            updateActive(eu > 0);
            operatingTicks += eu;

            int totalEU = OPERATION_TICKS * EU_USAGE;
            int completion = operatingTicks / totalEU;
            operatingTicks = operatingTicks % totalEU;

            if (completion == 1) {
                slotToInsert.setKey(ItemVariant.of(nextBlockToMine.asItem()));
                slotToInsert.increment(1);

                level.setBlock(posToMine, REPLACEMENT_BLOCK.defaultBlockState(), 2 + 4);
                blockToMine = calculateNextBlockToMine();
            }
            getInventory().autoExtractItems(level, worldPosition, orientation.outputDirection);
            setChanged();
        }
    }
}

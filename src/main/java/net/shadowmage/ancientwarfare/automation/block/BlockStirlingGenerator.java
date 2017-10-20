package net.shadowmage.ancientwarfare.automation.block;

import codechicken.lib.model.ModelRegistryHelper;
import codechicken.lib.model.bakery.CCBakeryModel;
import codechicken.lib.model.bakery.IBakeryProvider;
import codechicken.lib.model.bakery.ModelBakery;
import codechicken.lib.model.bakery.generation.IBakery;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.shadowmage.ancientwarfare.automation.gui.GuiStirlingGenerator;
import net.shadowmage.ancientwarfare.automation.render.StirlingGeneratorRenderer;
import net.shadowmage.ancientwarfare.automation.render.property.AutomationProperties;
import net.shadowmage.ancientwarfare.automation.tile.torque.TileStirlingGenerator;
import net.shadowmage.ancientwarfare.core.AncientWarfareCore;
import net.shadowmage.ancientwarfare.core.network.NetworkHandler;
import net.shadowmage.ancientwarfare.core.render.property.CoreProperties;
import net.shadowmage.ancientwarfare.core.util.ModelLoaderHelper;

public class BlockStirlingGenerator extends BlockTorqueGenerator implements IBakeryProvider {

    public BlockStirlingGenerator(String regName) {
        super(regName);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer.Builder(this)
                .add(CoreProperties.UNLISTED_FACING)
                .add(AutomationProperties.ACTIVE)
                .add(AutomationProperties.DYNAMIC)
                .add(AutomationProperties.ROTATION)
                .build();
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return StirlingGeneratorRenderer.INSTANCE.handleState((IExtendedBlockState) state, world, pos);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileStirlingGenerator();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public IBakery getBakery() {
        return StirlingGeneratorRenderer.INSTANCE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerClient() {
        NetworkHandler.registerGui(NetworkHandler.GUI_STIRLING_GENERATOR, GuiStirlingGenerator.class);

        ModelLoader.setCustomStateMapper(this, new StateMapperBase() {
            @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return StirlingGeneratorRenderer.MODEL_LOCATION;
            }
        });

        ModelRegistryHelper.register(StirlingGeneratorRenderer.MODEL_LOCATION, new CCBakeryModel(AncientWarfareCore.modID + ":model/automation/stirling_generator") {
            @Override
            public TextureAtlasSprite getParticleTexture() {
                return StirlingGeneratorRenderer.INSTANCE.sprite;
            }
        });

        ModelLoaderHelper.registerItem(this, "automation", "normal");

        ModelBakery.registerBlockKeyGenerator(this, state -> state.getBlock().getRegistryName().toString()
                + "," + state.getValue(CoreProperties.UNLISTED_FACING).toString()
                + "," + state.getValue(AutomationProperties.DYNAMIC)
                + "," + String.format("%.6f", state.getValue(AutomationProperties.ROTATION))
        );
    }

}

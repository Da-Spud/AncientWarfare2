package net.shadowmage.ancientwarfare.core.util;

import com.google.common.collect.Lists;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.oredict.OreDictionary;
import net.shadowmage.ancientwarfare.core.inventory.ItemQuantityMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InventoryTools {
	public static boolean canInventoryHold(IItemHandler handler, ItemStack stack) {
		return insertItem(handler, stack, true).isEmpty();
	}

	public static boolean canInventoryHold(IItemHandler handler, List<ItemStack> stacks) {
		return insertItems(handler, stacks, true).isEmpty();
	}

	public static NonNullList<ItemStack> insertItems(IItemHandler handler, List<ItemStack> stacks, boolean simulate) {
		NonNullList<ItemStack> remainingItems = NonNullList.create();
		if (simulate) {
			handler = cloneItemHandler(handler);
		}
		for (ItemStack stack : stacks) {
			ItemStack remainingItem = insertItem(handler, stack, false);
			if (!remainingItem.isEmpty()) {
				remainingItems.add(remainingItem);
			}
		}
		return remainingItems;
	}

	private static IItemHandler cloneItemHandler(IItemHandler handler) {
		ItemStackHandler copy = new ItemStackHandler(handler.getSlots());

		for (int slot = 0; slot < handler.getSlots(); slot++) {
			copy.setStackInSlot(slot, handler.getStackInSlot(slot).copy());
		}
		return copy;
	}

	public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			remaining = handler.insertItem(slot, remaining, simulate);
			if (remaining.isEmpty()) {
				break;
			}
		}
		return remaining;
	}

	public static void updateCursorItem(EntityPlayerMP player, ItemStack stack, boolean shiftClick) {
		if (!stack.isEmpty()) {
			if (shiftClick) {
				stack = mergeItemStack(player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null), stack);
			}
			if (!stack.isEmpty()) {
				player.inventory.setItemStack(stack);
				player.updateHeldItem();
			}
		}
	}

	public static ItemStack mergeItemStack(IItemHandler handler, ItemStack stack) {
		ItemStack ret = stack;
		if (stack.isStackable()) {
			for (int i = 0; i < handler.getSlots(); i++) {
				if (stack.isEmpty()) {
					break;
				}

				ItemStack slotStack = handler.getStackInSlot(i);

				if (doItemStacksMatch(slotStack, stack)) {
					int maxSize = Math.min(handler.getSlotLimit(i), stack.getMaxStackSize());

					int change = Math.min(maxSize - slotStack.getCount(), stack.getCount());

					handler.insertItem(i, stack.copy(), false);
					stack.shrink(change);
				}
			}
		}

		if (!stack.isEmpty()) {
			for (int i = 0; i < handler.getSlots(); i++) {
				ItemStack itemstack1 = handler.getStackInSlot(i);

				if (itemstack1.isEmpty() && handler.insertItem(i, stack, true).isEmpty()) {
					int slotLimit = handler.getSlotLimit(i);
					if (stack.getCount() > slotLimit) {
						handler.insertItem(i, stack.splitStack(slotLimit), false);
					} else {
						handler.insertItem(i, stack.splitStack(stack.getCount()), false);
						ret = ItemStack.EMPTY;
						break;
					}
				}
			}
		}

		return ret;
	}

	/*
	 * Attempts to remove filter * quantity from inventory.  Returns removed item in return stack, or null if
	 * no items were removed.<br>
	 * Will only remove and return up to filter.getMaxStackSize() items, regardless of how many are requested.
	 *
	 * @return the removed item.
	 */
	public static ItemStack removeItems(IItemHandler handler, ItemStack filter, int quantity) {
		if (quantity <= 0) {
			return ItemStack.EMPTY;
		}
		if (quantity > filter.getMaxStackSize()) {
			quantity = filter.getMaxStackSize();
		}
		int returnCount = 0;
		@Nonnull ItemStack slotStack;
		for (int index = 0; index < handler.getSlots(); index++) {
			slotStack = handler.getStackInSlot(index);
			if (slotStack.isEmpty() || !doItemStacksMatchRelaxed(filter, slotStack)) {
				continue;
			}

			int toMove = Math.min(quantity - returnCount, slotStack.getCount());
			returnCount += toMove;

			handler.extractItem(index, toMove, false);

			if (quantity - returnCount <= 0) {
				break;
			}
		}
		@Nonnull ItemStack returnStack = ItemStack.EMPTY;
		if (returnCount > 0) {
			returnStack = filter.copy();
			returnStack.setCount(returnCount);
		}
		return returnStack;
	}

	/*
	 * Move up to the specified quantity of filter stack from 'from' into 'to', using the designated sides (or general all sides merge if side<0 or from/to are not sided inventories)
	 *
	 * @param from     the inventory to withdraw items from
	 * @param to       the inventory to deposit items into
	 * @param filter   the stack used as a filter, only items matching this will be moved
	 * @param quantity how many items to move
	 * @param fromSide the side of 'from' inventory to withdraw out of
	 * @param toSide   the side of 'to' inventory to deposit into
	 */
	public static int transferItems(IItemHandler from, IItemHandler to, ItemStack filter, int quantity) {
		return transferItems(from, to, filter, quantity, false, false);
	}

	/*
	 * Move up to the specified quantity of filter stack from 'from' into 'to', using the designated sides (or general all sides merge if side<0 or from/to are not sided inventories)
	 *
	 * @param from         the inventory to withdraw items from
	 * @param to           the inventory to deposit items into
	 * @param filter       the stack used as a filter, only items matching this will be moved
	 * @param quantity     how many items to move
	 * @param fromSide     the side of 'from' inventory to withdraw out of
	 * @param toSide       the side of 'to' inventory to deposit into
	 * @param ignoreDamage ignore item-damage when looking for items to move
	 * @param ignoreNBT    ignore item-tag when looking for items to move
	 */
	public static int transferItems(IItemHandler from, IItemHandler to, ItemStack filter, int quantity, boolean ignoreDamage, boolean ignoreNBT) {
		int moved = 0;
		@Nonnull ItemStack s1, s2;
		int toMove = quantity;
		int stackSize;
		for (int fromIndex = 0; fromIndex < from.getSlots(); fromIndex++) {
			s1 = from.getStackInSlot(fromIndex);
			if (s1.isEmpty() || !doItemStacksMatch(filter, s1, ignoreDamage, ignoreNBT)) {
				continue;
			}
			stackSize = s1.getCount();
			if (s1.getCount() > toMove)//move partial stack
			{
				s2 = s1.copy();
				s2.setCount(toMove);
				s1.shrink(toMove);
				stackSize = s2.getCount();
				s2 = mergeItemStack(to, s2);
				if (!s2.isEmpty())//partial merge, destination full, break out
				{
					moved += stackSize - s2.getCount();
					mergeItemStack(from, s2);//put back the remainder of the partial stack that was copied out
					break;
				} else {
					moved += stackSize;
					toMove -= stackSize;
				}
			} else {
				s1 = mergeItemStack(to, s1);
				if (!s1.isEmpty())//destination inventory was full, break out
				{
					moved += stackSize - s1.getCount();
					break;
				} else {
					moved += stackSize;
					toMove -= stackSize;
				}
			}
			if (toMove <= 0) {
				break;
			}
		}
		return moved;
	}

	public static int findItemSlot(IItemHandler handler, Predicate<ItemStack> filter) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			@Nonnull ItemStack stack = handler.getStackInSlot(slot);
			if (filter.test(stack)) {
				return slot;
			}
		}
		return -1;
	}

	public static int getCountOf(IItemHandler handler, ItemStack filterStack) {
		return getCountOf(handler, stack -> !stack.isEmpty() && doItemStacksMatchRelaxed(filterStack, stack));
	}

	public static int getCountOf(IItemHandler handler, Predicate<ItemStack> filter) {
		if (handler.getSlots() <= 0) {
			return 0;
		}
		int count = 0;
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			@Nonnull ItemStack stack = handler.getStackInSlot(slot);
			if (filter.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/*
	 * validates that stacks are the same item / damage / tag, ignores quantity
	 */
	public static boolean doItemStacksMatchRelaxed(ItemStack stack1, ItemStack stack2) {
		if (stack1 == stack2) {
			return true;
		}
		return OreDictionary.itemMatches(stack1, stack2, !stack1.isEmpty() && (stack1.isItemStackDamageable() || stack1.getItemDamage() != OreDictionary.WILDCARD_VALUE)) && ItemStack.areItemsEqualIgnoreDurability(stack1, stack2) && stack1.areCapsCompatible(stack2);
	}

	public static boolean areItemStackTagsEqual(ItemStack stackA, ItemStack stackB) {
		if (stackA.isEmpty() && stackB.isEmpty()) {
			return true;
		} else if (!stackA.isEmpty() && !stackB.isEmpty()) {
			if ((stackA.getTagCompound() == null || stackA.getTagCompound().hasNoTags()) && (stackB.getTagCompound() != null && !stackB.getTagCompound().hasNoTags())) {
				return false;
			} else {
				return (stackA.getTagCompound() == null || stackA.getTagCompound().equals(stackB.getTagCompound())) && stackA.areCapsCompatible(stackB);
			}
		} else {
			return false;
		}
	}

	public static boolean doItemStacksMatch(ItemStack stackA, ItemStack stackB) {
		return doItemStacksMatch(stackA, stackB, false, false);
	}

	public static boolean doItemStacksMatch(ItemStack stackA, ItemStack stackB, boolean ignoreDamage, boolean ignoreNBT) {
		if (stackA.isEmpty() && stackB.isEmpty()) {
			return true;
		} else if (stackA.getItem() != stackB.getItem()) {
			return false;
		} else if ((stackA.getHasSubtypes() || !ignoreDamage) && stackA.getItemDamage() != stackB.getItemDamage()) {
			return false;
		} else if (!ignoreNBT && stackA.getTagCompound() == null && stackB.getTagCompound() != null) {
			return false;
		} else {
			return (ignoreNBT || stackA.getTagCompound() == null || stackA.getTagCompound().equals(stackB.getTagCompound())) && stackA.areCapsCompatible(stackB);
		}
	}

	/*
	 * drops the input itemstack into the world at the input position
	 */
	public static void dropItemInWorld(World world, ItemStack item, BlockPos pos) {
		dropItemInWorld(world, item, pos.getX(), pos.getY(), pos.getZ());
	}

	public static void dropItemInWorld(World world, ItemStack item, double x, double y, double z) {
		if (item.isEmpty() || world == null || world.isRemote) {
			return;
		}
		EntityItem entityToSpawn;
		x += world.rand.nextFloat() * 0.6f - 0.3f;
		y += world.rand.nextFloat() * 0.6f + 1 - 0.3f;
		z += world.rand.nextFloat() * 0.6f - 0.3f;
		entityToSpawn = new EntityItem(world, x, y, z, item);
		world.spawnEntity(entityToSpawn);
	}

	public static void dropInventoryInWorld(@Nullable TileEntity te) {
		if (te == null) {
			return;
		}

		IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

		if (handler != null) {
			dropInventoryInWorld(te.getWorld(), handler, te.getPos());
		}
	}

	public static void dropInventoryInWorld(World world, IItemHandler inventory, BlockPos pos) {
		if (world.isRemote) {
			return;
		}
		if (inventory != null) {
			@Nonnull ItemStack stack;
			for (int i = 0; i < inventory.getSlots(); i++) {
				stack = inventory.getStackInSlot(i);
				if (stack.isEmpty()) {
					continue;
				}
				dropItemInWorld(world, inventory.extractItem(i, stack.getCount(), false), pos.getX(), pos.getY(), pos.getZ());
			}
		}
	}

	public static void dropInventoryInWorld(World world, IInventory localInventory, BlockPos pos) {
		dropInventoryInWorld(world, localInventory, pos.getX(), pos.getY(), pos.getZ());
	}

	public static void dropInventoryInWorld(World world, IInventory localInventory, double x, double y, double z) {
		if (world.isRemote) {
			return;
		}
		if (localInventory != null) {
			@Nonnull ItemStack stack;
			for (int i = 0; i < localInventory.getSizeInventory(); i++) {
				stack = localInventory.removeStackFromSlot(i);
				if (stack.isEmpty()) {
					continue;
				}
				dropItemInWorld(world, stack, x, y, z);
			}
		}
	}

	/*
	 * Writes out the input inventory to the input nbt-tag.<br>
	 * The written out inventory is suitable for reading back using
	 * {@link InventoryTools#readInventoryFromNBT(IInventory, NBTTagCompound)}
	 */
	public static NBTTagCompound writeInventoryToNBT(IInventory inventory, NBTTagCompound tag) {
		NBTTagList itemList = new NBTTagList();
		NBTTagCompound itemTag;
		@Nonnull ItemStack item;
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			item = inventory.getStackInSlot(i);
			if (item.isEmpty()) {
				continue;
			}
			itemTag = item.writeToNBT(new NBTTagCompound());
			itemTag.setShort("slot", (short) i);
			itemList.appendTag(itemTag);
		}
		tag.setTag("itemList", itemList);
		return tag;//TODO clean up all references to this to use single-line semantics
	}

	/*
	 * Reads an inventory contents into the input inventory from the given nbt-tag.<br>
	 * Should only be passed nbt-tags / inventories that have been saved using
	 * {@link InventoryTools#writeInventoryToNBT(IInventory, NBTTagCompound)}
	 */
	public static void readInventoryFromNBT(IInventory inventory, NBTTagCompound tag) {
		NBTTagList itemList = tag.getTagList("itemList", Constants.NBT.TAG_COMPOUND);
		NBTTagCompound itemTag;
		@Nonnull ItemStack item;
		int slot;
		for (int i = 0; i < itemList.tagCount(); i++) {
			itemTag = itemList.getCompoundTagAt(i);
			slot = itemTag.getShort("slot");
			item = new ItemStack(itemTag);
			inventory.setInventorySlotContents(slot, item);
		}
	}

	/*
	 * Compacts in input item-stack list.<br>
	 * This particular method wraps an ItemQuantityMap, and has much better speed than the other two methods,
	 * but does use more memory in the process.  On average 2x faster than compactStackList and 4x+ faster than
	 * compacctStackList2
	 */
	public static List<ItemStack> compactStackList(List<ItemStack> in) {
		ItemQuantityMap map = new ItemQuantityMap();
		for (ItemStack stack : in) {
			map.addCount(stack, stack.getCount());
		}
		return map.getItems();
	}

	public static void mergeItemStacks(List<ItemStack> stacks, List<ItemStack> stacksToMerge) {
		for (ItemStack stackToMerge : stacksToMerge) {
			if (stackToMerge.isEmpty()) {
				continue;
			}

			for (ItemStack stack : stacks) {
				if (stack.getCount() < stack.getMaxStackSize() && ItemHandlerHelper.canItemStacksStack(stackToMerge, stack)) {
					int count = Math.min(stack.getMaxStackSize() - stack.getCount(), stackToMerge.getCount());
					stack.grow(count);
					stackToMerge.shrink(count);
					if (stackToMerge.isEmpty()) {
						break;
					}
				}
			}
			if (!stackToMerge.isEmpty()) {
				stacks.add(stackToMerge);
			}
		}
	}

	public static void dropItemsInWorld(World world, IItemHandler handler, BlockPos pos) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			dropItemInWorld(world, handler.getStackInSlot(slot), pos);
		}
	}

	public static void dropItemsInWorld(World world, List<ItemStack> stacks, BlockPos pos) {
		for (ItemStack stack : stacks) {
			dropItemInWorld(world, stack, pos);
		}
	}

	/*
	 * Item-stack comparator.  Configurable in constructor to sort by localized or unlocalized name, as well as
	 * sort-order (regular or reverse).
	 *
	 * @author Shadowmage
	 */

	public static final class ComparatorItemStack implements Comparator<ItemStack> {

		public enum SortType {
			QUANTITY("sort_type_quantity") {
				@Override
				public int compare(ItemStack o1, ItemStack o2) {
					int r = o1.getCount() - o2.getCount();
					if (r == 0) {
						return super.compare(o1, o2);
					}
					return r;
				}
			}, NAME("sort_type_name") {
				@Override
				public int compare(ItemStack o1, ItemStack o2) {
					int r = o1.getDisplayName().compareTo(o2.getDisplayName());
					if (r == 0) {//if they have the same name, compare damage/tags
						return super.compare(o1, o2);
					}
					return r;
				}
			}, DAMAGE("sort_type_damage");

			public final String unlocalizedName;

			SortType(String unlocalizedName) {
				this.unlocalizedName = unlocalizedName;
			}

			public SortType next() {
				if (this == QUANTITY)
					return NAME;
				else if (this == NAME)
					return DAMAGE;
				else
					return QUANTITY;
			}

			@Override
			public String toString() {
				return unlocalizedName;
			}

			public int compare(ItemStack o1, ItemStack o2) {
				if (o1.getItemDamage() != o2.getItemDamage()) {
					return o1.getItemDamage() - o2.getItemDamage();
				} else {
					if (o1.hasTagCompound()) {
						if (o2.hasTagCompound())
							return o1.getTagCompound().hashCode() - o2.getTagCompound().hashCode();
						else
							return 1;
					} else if (o2.hasTagCompound()) {
						return -1;
					}
				}
				return 0;
			}
		}

		public static enum SortOrder {
			ASCENDING(-1), DESCENDING(1);

			SortOrder(int mult) {
				this.mult = mult;
			}

			int mult;
		}

		private SortOrder sortOrder;
		private SortType sortType;
		private String textInput = "";

		/*
		 * @param order 1 for normal, -1 for reverse
		 */
		public ComparatorItemStack(SortType type, SortOrder order) {
			this.sortOrder = order;
			this.sortType = type;
		}

		public void setTextInput(String text) {
			if (text == null) {
				text = "";
			}
			this.textInput = text;
		}

		public void setSortOrder(SortOrder order) {
			this.sortOrder = order;
		}

		public void setSortType(SortType type) {
			this.sortType = type;
		}

		@Override
		public int compare(ItemStack o1, ItemStack o2) {
			int val;
			if (!textInput.isEmpty()) {
				val = compareViaTextInput(o1, o2);
			} else {
				val = sortType.compare(o1, o2);
			}
			return val * sortOrder.mult;
		}

		private int compareViaTextInput(ItemStack o1, ItemStack o2) {
			String input = textInput.toLowerCase(Locale.ENGLISH);
			String n1 = o1.getDisplayName().toLowerCase(Locale.ENGLISH), n2 = o2.getDisplayName().toLowerCase(Locale.ENGLISH);
			if (n1.startsWith(input)) {
				if (!n2.startsWith(input))
					return 1;
			} else if (n2.startsWith(input)) {
				return -1;
			} else if (n1.contains(input)) {
				if (!n2.contains(input))
					return 1;
			} else if (n2.contains(input)) {
				return -1;
			}
			return sortType.compare(o1, o2);
		}
	}

	public static List<Integer> getEmptySlotsRandomized(IInventory inventory, Random rand) {
		List<Integer> list = Lists.newArrayList();

		for (int i = 0; i < inventory.getSizeInventory(); ++i) {
			if (inventory.getStackInSlot(i).isEmpty()) {
				list.add(i);
			}
		}

		Collections.shuffle(list, rand);
		return list;
	}

	public static void shuffleItems(List<ItemStack> stacks, int numberOfSlots, Random rand) {
		numberOfSlots = numberOfSlots - stacks.size();

		int MIN_SIZE_TO_SPLIT = 3;

		List<ItemStack> splittableStacks = stacks.stream().filter(s -> (s.getCount() >= MIN_SIZE_TO_SPLIT)).collect(Collectors.toList());

		while (numberOfSlots > 0 && !splittableStacks.isEmpty()) {
			int slot = rand.nextInt(splittableStacks.size());

			ItemStack stack = splittableStacks.get(slot);

			int splitCount = MathHelper.getInt(rand, 1, stack.getCount() / 2);
			ItemStack splitStack = stack.splitStack(splitCount);

			if (stack.getCount() < MIN_SIZE_TO_SPLIT) {
				splittableStacks.remove(slot);
			}

			if (splitStack.getCount() >= MIN_SIZE_TO_SPLIT) {
				splittableStacks.add(splitStack);
			}

			stacks.add(splitStack);

			numberOfSlots--;
		}

		Collections.shuffle(stacks, rand);
	}

	public static void insertOrDropItem(IItemHandler handler, ItemStack stack, World world, BlockPos pos) {
		ItemStack remaining = insertItem(handler, stack, false);
		if (!remaining.isEmpty()) {
			dropItemInWorld(world, stack, pos);
		}
	}

	public static void insertOrDropItems(IItemHandler handler, List<ItemStack> stacks, World world, BlockPos pos) {
		for (ItemStack stack : stacks) {
			insertOrDropItem(handler, stack, world, pos);
		}
	}
}

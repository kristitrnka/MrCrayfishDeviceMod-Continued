package com.mrcrayfish.device.network.task;

import com.mrcrayfish.device.api.task.Task;
import com.mrcrayfish.device.api.task.TaskManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageResponse implements IMessage, IMessageHandler<MessageResponse, IMessage>
{
	private int id;
	private Task request;
	private NBTTagCompound nbt;

	public MessageResponse()
	{
	}

	public MessageResponse(int id, Task request)
	{
		this.id = id;
		this.request = request;
	}

	@Override
	public IMessage onMessage(MessageResponse message, MessageContext ctx)
	{
		/*
		 * Při přehrávání přes Replay Mod neexistuje původní Task,
		 * protože nebyl v této herní relaci vytvořen.
		 *
		 * Původní kód zde zavolal processResponse() na null
		 * a způsobil fatal network disconnect.
		 */
		if (message.request == null)
		{
			return null;
		}

		if (message.nbt == null)
		{
			message.nbt = new NBTTagCompound();
		}

		message.request.processResponse(message.nbt);
		message.request.callback(message.nbt);

		return null;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		this.id = buf.readInt();
		boolean successful = buf.readBoolean();

		/*
		 * V normální hře Task existuje v TaskManageru.
		 * V Replay Modu ale seznam původních tasků neexistuje,
		 * takže getTaskAndRemove() může vrátit null.
		 */
		this.request = TaskManager.getTaskAndRemove(this.id);

		/*
		 * Data musíme z bufferu přečíst vždy, i když task neexistuje.
		 * Jinak by paket nebyl správně celý zpracovaný.
		 */
		ByteBufUtils.readUTF8String(buf);
		this.nbt = ByteBufUtils.readTag(buf);

		if (this.request == null)
		{
			return;
		}

		if (successful)
		{
			this.request.setSuccessful();
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		if (this.request == null)
		{
			throw new IllegalStateException(
					"Cannot encode MessageResponse because the request task is null"
			);
		}

		buf.writeInt(this.id);
		buf.writeBoolean(this.request.isSucessful());
		ByteBufUtils.writeUTF8String(buf, this.request.getName());

		NBTTagCompound nbt = new NBTTagCompound();
		this.request.prepareResponse(nbt);
		ByteBufUtils.writeTag(buf, nbt);

		this.request.complete();
	}
}
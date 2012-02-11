package org.andengine.opengl.vbo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.andengine.opengl.shader.ShaderProgram;
import org.andengine.opengl.util.BufferUtils;
import org.andengine.opengl.util.GLState;
import org.andengine.opengl.vbo.VertexBufferObject.DrawType;
import org.andengine.opengl.vbo.attribute.VertexBufferObjectAttributes;
import org.andengine.util.adt.DataConstants;
import org.andengine.util.system.SystemUtils;

import android.opengl.GLES20;
import android.os.Build;

/**
 * Compared to a {@link HighPerformanceVertexBufferObject}, the {@link ZeroMemoryVertexBufferObject} uses <b><u>100%</u> less heap memory</b>,
 * at the cost of expensive data buffering (<b>up to <u>5x</u> slower!</b>) whenever the .
 * <p/>
 * Usually a {@link ZeroMemoryVertexBufferObject} is preferred to a {@link HighPerformanceVertexBufferObject} when the following conditions are met:
 * <ol>
 * <li>The applications is close to run out of memory.</li>
 * <li>You have very big {@link HighPerformanceVertexBufferObject} or an extreme number of small {@link HighPerformanceVertexBufferObject}s, where you can't afford to have any of the bufferdata to remain in heap memory.</li>
 * <li>The content (color, vertices, texturecoordinates) of the {@link ZeroMemoryVertexBufferObject} is changed not often, or even better: never.</li>
 * </ol>
 * <p/>
 * (c) Zynga 2011
 *
 * @author Nicolas Gramlich <ngramlich@zynga.com>
 * @author Greg Haynes
 * @since 19:03:32 - 10.02.2012
 */
public abstract class ZeroMemoryVertexBufferObject implements IVertexBufferObject {
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	protected final int mCapacity;
	protected final boolean mAutoDispose;
	protected final int mUsage;

	protected int mHardwareBufferID = IVertexBufferObject.HARDWARE_BUFFER_ID_INVALID;
	protected boolean mDirtyOnHardware = true;

	protected boolean mDisposed;

	protected final VertexBufferObjectManager mVertexBufferObjectManager;
	protected final VertexBufferObjectAttributes mVertexBufferObjectAttributes;

	// ===========================================================
	// Constructors
	// ===========================================================

	public ZeroMemoryVertexBufferObject(final VertexBufferObjectManager pVertexBufferObjectManager, final int pCapacity, final DrawType pDrawType, final boolean pAutoDispose, final VertexBufferObjectAttributes pVertexBufferObjectAttributes) {
		this.mVertexBufferObjectManager = pVertexBufferObjectManager;
		this.mCapacity = pCapacity;
		this.mUsage = pDrawType.getUsage();
		this.mAutoDispose = pAutoDispose;
		this.mVertexBufferObjectAttributes = pVertexBufferObjectAttributes;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	@Override
	public VertexBufferObjectManager getVertexBufferObjectManager() {
		return this.mVertexBufferObjectManager;
	}

	@Override
	public boolean isDisposed() {
		return this.mDisposed;
	}

	@Override
	public boolean isAutoDispose() {
		return this.mAutoDispose;
	}

	@Override
	public int getHardwareBufferID() {
		return this.mHardwareBufferID;
	}

	@Override
	public boolean isLoadedToHardware() {
		return this.mHardwareBufferID != IVertexBufferObject.HARDWARE_BUFFER_ID_INVALID;
	}

	@Override
	public void setNotLoadedToHardware() {
		this.mHardwareBufferID = IVertexBufferObject.HARDWARE_BUFFER_ID_INVALID;
		this.mDirtyOnHardware = true;
	}

	@Override
	public boolean isDirtyOnHardware() {
		return this.mDirtyOnHardware;
	}

	@Override
	public void setDirtyOnHardware() {
		this.mDirtyOnHardware = true;
	}

	@Override
	public int getCapacity() {
		return this.mCapacity;
	}

	@Override
	public int getByteCapacity() {
		return this.mCapacity * DataConstants.BYTES_PER_FLOAT;
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	protected abstract void onPopulateBufferData(final ByteBuffer byteBuffer);

	@Override
	public void bind(final GLState pGLState) {
		if(this.mHardwareBufferID == IVertexBufferObject.HARDWARE_BUFFER_ID_INVALID) {
			this.loadToHardware(pGLState);
			this.mVertexBufferObjectManager.onVertexBufferObjectLoaded(this);
		}

		pGLState.bindBuffer(this.mHardwareBufferID);

		if(this.mDirtyOnHardware) {
			final ByteBuffer byteBuffer = this.aquireByteBuffer();

			this.onPopulateBufferData(byteBuffer);

			GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, this.mUsage);

			this.releaseByteBuffer(byteBuffer);

			this.mDirtyOnHardware = false;
		}
	}

	@Override
	public void bind(final GLState pGLState, final ShaderProgram pShaderProgram) {
		this.bind(pGLState);

		pShaderProgram.bind(pGLState, this.mVertexBufferObjectAttributes);
	}

	@Override
	public void unbind(final GLState pGLState, final ShaderProgram pShaderProgram) {
		pShaderProgram.unbind(pGLState);

		// pGLState.bindBuffer(0); // TODO Does this have an positive/negative impact on performance?
	}

	@Override
	public void unloadFromHardware(final GLState pGLState) {
		pGLState.deleteBuffer(this.mHardwareBufferID);

		this.mHardwareBufferID = IVertexBufferObject.HARDWARE_BUFFER_ID_INVALID;
	}

	@Override
	public void draw(final int pPrimitiveType, final int pCount) {
		GLES20.glDrawArrays(pPrimitiveType, 0, pCount);
	}

	@Override
	public void draw(final int pPrimitiveType, final int pOffset, final int pCount) {
		GLES20.glDrawArrays(pPrimitiveType, pOffset, pCount);
	}

	@Override
	public void dispose() {
		if(!this.mDisposed) {
			this.mDisposed = true;

			this.mVertexBufferObjectManager.onUnloadVertexBufferObject(this);
		} else {
			throw new AlreadyDisposedException();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();

		if(!this.mDisposed) {
			this.dispose();
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void loadToHardware(final GLState pGLState) {
		this.mHardwareBufferID = pGLState.generateBuffer();
		this.mDirtyOnHardware = true;
	}

	protected ByteBuffer aquireByteBuffer() {
		final ByteBuffer byteBuffer;
		if(SystemUtils.isAndroidVersion(Build.VERSION_CODES.HONEYCOMB, Build.VERSION_CODES.HONEYCOMB_MR2)) {
			/* Honeycomb workaround for issue 16941. */
			byteBuffer = BufferUtils.allocateDirect(this.mCapacity * DataConstants.BYTES_PER_FLOAT);
		} else {
			/* Other SDK versions. */
			byteBuffer = ByteBuffer.allocateDirect(this.mCapacity * DataConstants.BYTES_PER_FLOAT);
		}
		byteBuffer.order(ByteOrder.nativeOrder());
		return byteBuffer;
	}

	protected void releaseByteBuffer(final ByteBuffer byteBuffer) {
		/* Cleanup due to 'Honeycomb workaround for issue 16941' in constructor. */
		if(SystemUtils.isAndroidVersion(Build.VERSION_CODES.HONEYCOMB, Build.VERSION_CODES.HONEYCOMB_MR2)) {
			BufferUtils.freeDirect(byteBuffer);
		}
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
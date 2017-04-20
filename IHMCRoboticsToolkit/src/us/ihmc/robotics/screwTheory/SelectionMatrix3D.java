package us.ihmc.robotics.screwTheory;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.MatrixDimensionException;

import us.ihmc.robotics.geometry.FrameMatrix3D;
import us.ihmc.robotics.linearAlgebra.MatrixTools;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;

/**
 * The {@code SelectionMatrix3D} provides a simple way to define for a given application what are
 * the axes of interest.
 * <p>
 * Given the set of axes of interest and a reference frame to which these axes refer to, the
 * {@code SelectionMatrix3D} is then able compute the corresponding 3-by-3 selection matrix.
 * </p>
 * <p>
 * The principal use-case is for the controller core notably used for the walking controller. This
 * class can be used to clearly define what the axes to be controlled for a given end-effector.
 * </p>
 * <p>
 * Note that the {@link #selectionFrame} is optional. It is preferable to provide it when possible,
 * but when it is absent, i.e. equal to {@code null}, the selection matrix will then be generated
 * assuming the destination frame is the same as the selection frame.
 * </p>
 * 
 * @author Sylvain Bertrand
 *
 */
public class SelectionMatrix3D
{
   /**
    * When selecting the axes of interest, these axes refer to selection frame axes. This frame is
    * optional. It is preferable to provide it when possible, but when it is absent, i.e. equal to
    * {@code null}, the selection matrix will then be generated assuming the destination frame is
    * the same as the selection frame.
    * <p>
    * Note that if all the axes are selected or none of them is, the selection matrix becomes
    * independent from its selection frame.
    * </p>
    */
   private ReferenceFrame selectionFrame = null;
   /** Specifies whether the x-axis of the selection frame is an axis of interest. */
   private boolean xSelected = true;
   /** Specifies whether the y-axis of the selection frame is an axis of interest. */
   private boolean ySelected = true;
   /** Specifies whether the z-axis of the selection frame is an axis of interest. */
   private boolean zSelected = true;

   /**
    * Internal object used only to convert the three booleans into an actual 3-by-3 selection
    * matrix.
    */
   private final FrameMatrix3D frameMatrix = new FrameMatrix3D();

   /**
    * Creates a new selection matrix. This selection matrix is initialized with all the axes
    * selected. Until the selection is changed, this selection matrix is independent from its
    * selection frame.
    */
   public SelectionMatrix3D()
   {
   }

   /**
    * Sets the selection frame to {@code null}.
    * <p>
    * When the selection frame is {@code null}, the conversion into a 3-by-3 selection matrix will
    * be done regardless of the destination frame.
    * </p>
    */
   public void clearSelectionFrame()
   {
      setSelectionFrame(null);
   }

   /**
    * Sets the selection frame such that the selection of the axes of interest now refers to the
    * axes of the given frame.
    * 
    * @param selectionFrame the new frame to which the axes selection is referring to.
    */
   public void setSelectionFrame(ReferenceFrame selectionFrame)
   {
      this.selectionFrame = selectionFrame;
   }

   /**
    * Selects all the axes and clears the selection frame.
    * <p>
    * Until the selection is changed, this selection matrix is independent from its selection frame.
    * </p>
    */
   public void resetSelection()
   {
      selectionFrame = null;
      xSelected = true;
      ySelected = true;
      zSelected = true;
   }

   /**
    * Deselects all the axes and clears the selection frame.
    * <p>
    * Until the selection is changed, this selection matrix is independent from its selection frame.
    * </p>
    */
   public void clearSelection()
   {
      selectionFrame = null;
      xSelected = false;
      ySelected = false;
      zSelected = false;
   }

   /**
    * Updates the selection of the axes of interest.
    * <p>
    * Note that it is preferable to also set selection frame to which this selection is referring
    * to.
    * </p>
    * 
    * @param xSelected whether the x-axis is an axis of interest.
    * @param ySelected whether the y-axis is an axis of interest.
    * @param zSelected whether the z-axis is an axis of interest.
    */
   public void setAxisSelection(boolean xSelected, boolean ySelected, boolean zSelected)
   {
      this.xSelected = xSelected;
      this.ySelected = ySelected;
      this.zSelected = zSelected;
   }

   /**
    * Updates the selection state for the x-axis.
    * <p>
    * Note that it is preferable to also set selection frame to which this selection is referring
    * to.
    * </p>
    * 
    * @param select whether the x-axis is an axis of interest.
    */
   public void selectXAxis(boolean select)
   {
      xSelected = select;
   }

   /**
    * Updates the selection state for the y-axis.
    * <p>
    * Note that it is preferable to also set selection frame to which this selection is referring
    * to.
    * </p>
    * 
    * @param select whether the y-axis is an axis of interest.
    */
   public void selectYAxis(boolean select)
   {
      ySelected = select;
   }

   /**
    * Updates the selection state for the z-axis.
    * <p>
    * Note that it is preferable to also set selection frame to which this selection is referring
    * to.
    * </p>
    * 
    * @param select whether the z-axis is an axis of interest.
    */
   public void selectZAxis(boolean select)
   {
      zSelected = select;
   }

   /**
    * Converts this into an actual 3-by-3 selection matrix that is to be used with data expressed in
    * the {@code destinationFrame}.
    * <p>
    * Only the block (row=0, column=0) to (row=2, column=2) of {@code selectionMatrixToPack} is
    * edited to insert the selection matrix. The given dense-matrix is not reshaped.
    * </p>
    * 
    * @param destinationFrame the reference frame in which the selection matrix is to be used.
    * @param selectionMatrixToPack the dense-matrix into which the 3-by-3 selection matrix is to be
    *           inserted.
    * @throws MatrixDimensionException if the given matrix is too small.
    */
   public void getFullSelectionMatrixInFrame(ReferenceFrame destinationFrame, DenseMatrix64F selectionMatrixToPack)
   {
      getFullSelectionMatrixInFrame(destinationFrame, 0, 0, selectionMatrixToPack);
   }

   /**
    * Converts this into an actual 3-by-3 selection matrix that is to be used with data expressed in
    * the {@code destinationFrame}.
    * <p>
    * Only the block (row=startRow, column=startColumn) to (row=startRow + 2, column=startColumn+2)
    * of {@code selectionMatrixToPack} is edited to insert the selection matrix. The given
    * dense-matrix is not reshaped.
    * </p>
    * 
    * @param destinationFrame the reference frame in which the selection matrix is to be used.
    * @param startRow the first row index to start writing in the dense-matrix.
    * @param startColumn the first column index to start writing in the dense-matrix.
    * @param selectionMatrixToPack the dense-matrix into which the 3-by-3 selection matrix is to be
    *           inserted.
    * @throws MatrixDimensionException if the given matrix is too small.
    */
   public void getFullSelectionMatrixInFrame(ReferenceFrame destinationFrame, int startRow, int startColumn, DenseMatrix64F selectionMatrixToPack)
   {
      int numRows = selectionMatrixToPack.getNumRows();
      int numCols = selectionMatrixToPack.getNumCols();
      if (numRows < startRow + 3 || numCols < startColumn + 3)
         throw new MatrixDimensionException("The selection matrix has to be at least a " + (startRow + 3) + "-by-" + (startColumn + 3) + " but was instead a "
               + numRows + "-by-" + numCols + " matrix.");

      if (canIgnoreSelectionFrame(destinationFrame))
      {
         for (int row = startRow; row < startRow + 3; row++)
         {
            for (int column = startColumn; column < startColumn + 3; column++)
            {
               selectionMatrixToPack.set(row, column, 0.0);
            }
         }
         selectionMatrixToPack.set(startRow++, startColumn++, xSelected ? 1.0 : 0.0);
         selectionMatrixToPack.set(startRow++, startColumn++, ySelected ? 1.0 : 0.0);
         selectionMatrixToPack.set(startRow, startColumn, zSelected ? 1.0 : 0.0);
      }
      else
      {
         frameMatrix.setToZero(selectionFrame);
         frameMatrix.setM00(xSelected ? 1.0 : 0.0);
         frameMatrix.setM11(ySelected ? 1.0 : 0.0);
         frameMatrix.setM22(zSelected ? 1.0 : 0.0);
         frameMatrix.changeFrame(destinationFrame);

         frameMatrix.getDenseMatrix(selectionMatrixToPack, startRow, startColumn);
      }
   }

   /**
    * Converts this into an actual 3-by-3 selection matrix that is to be used with data expressed in
    * the {@code destinationFrame}.
    * <p>
    * In addition to what {@link #getFullSelectionMatrixInFrame(ReferenceFrame, DenseMatrix64F)}
    * does, this method also removes the zero-rows of the given selection matrix.
    * </p>
    * <p>
    * Only the block (row=0, column=0) to (row=2, column=2) of {@code selectionMatrixToPack} is
    * edited to insert the selection matrix. The given dense-matrix is not reshaped.
    * </p>
    * 
    * @param destinationFrame the reference frame in which the selection matrix is to be used.
    * @param selectionMatrixToPack the dense-matrix into which the 3-by-3 selection matrix is to be
    *           inserted.
    * @throws MatrixDimensionException if the given matrix is too small.
    */
   public void getEfficientSelectionMatrixInFrame(ReferenceFrame destinationFrame, DenseMatrix64F selectionMatrixToPack)
   {
      getEfficientSelectionMatrixInFrame(destinationFrame, 0, 0, selectionMatrixToPack);
   }

   /**
    * Converts this into an actual 3-by-3 selection matrix that is to be used with data expressed in
    * the {@code destinationFrame}.
    * <p>
    * In addition to what
    * {@link #getFullSelectionMatrixInFrame(ReferenceFrame, int, int, DenseMatrix64F)} does, this
    * method also removes the zero-rows of the given selection matrix.
    * </p>
    * <p>
    * Only the block (row=startRow, column=startColumn) to (row=startRow + 2, column=startColumn+2)
    * of {@code selectionMatrixToPack} is edited to insert the selection matrix. The given
    * dense-matrix is not reshaped.
    * </p>
    * 
    * @param destinationFrame the reference frame in which the selection matrix is to be used.
    * @param startRow the first row index to start writing in the dense-matrix.
    * @param startColumn the first column index to start writing in the dense-matrix.
    * @param selectionMatrixToPack the dense-matrix into which the 3-by-3 selection matrix is to be
    *           inserted.
    * @throws MatrixDimensionException if the given matrix is too small.
    */
   public void getEfficientSelectionMatrixInFrame(ReferenceFrame destinationFrame, int startRow, int startColumn, DenseMatrix64F selectionMatrixToPack)
   {
      int numRows = selectionMatrixToPack.getNumRows();
      int numCols = selectionMatrixToPack.getNumCols();
      if (numRows < startRow + 3 || numCols < startColumn + 3)
         throw new MatrixDimensionException("The selection matrix has to be at least a " + (startRow + 3) + "-by-" + (startColumn + 3) + " but was instead a "
               + numRows + "-by-" + numCols + " matrix.");

      if (canIgnoreSelectionFrame(destinationFrame))
      {
         for (int row = startRow; row < startRow + 3; row++)
         {
            for (int column = startColumn; column < startColumn + 3; column++)
            {
               selectionMatrixToPack.set(row, column, 0.0);
            }
         }
         if (!zSelected)
            MatrixTools.removeRow(selectionMatrixToPack, startRow + 2);
         else
            selectionMatrixToPack.set(startRow + 2, startColumn + 2, 1.0);

         if (!ySelected)
            MatrixTools.removeRow(selectionMatrixToPack, startRow + 1);
         else
            selectionMatrixToPack.set(startRow + 1, startColumn + 1, 1.0);

         if (!xSelected)
            MatrixTools.removeRow(selectionMatrixToPack, startRow);
         else
            selectionMatrixToPack.set(startRow, startColumn, 1.0);
      }
      else
      {
         frameMatrix.setToZero(selectionFrame);
         frameMatrix.setM00(xSelected ? 1.0 : 0.0);
         frameMatrix.setM11(ySelected ? 1.0 : 0.0);
         frameMatrix.setM22(zSelected ? 1.0 : 0.0);
         frameMatrix.changeFrame(destinationFrame);

         frameMatrix.getDenseMatrix(selectionMatrixToPack, startRow, startColumn);
         MatrixTools.removeZeroRows(selectionMatrixToPack, startRow, startRow + 2, 1.0e-7);
      }
   }

   /**
    * Internal method to determine if this selection frame is frame independent or not.
    * 
    * @param destinationFrame the frame into which the 3-by-3 selection matrix is about to be
    *           converted.
    * @return {@code true} if this is frame independent and thus there is no need to consider
    *         changing the frame to {@code destinationFrame}, {@code false} if the change of frame
    *         has to be performed.
    */
   private boolean canIgnoreSelectionFrame(ReferenceFrame destinationFrame)
   {
      if (selectionFrame == null)
         return true;
      if (selectionFrame == destinationFrame)
         return true;
      if (xSelected && ySelected && zSelected)
         return true;
      if (!xSelected && !ySelected && !zSelected)
         return true;
      return false;
   }

   /**
    * Whether the x-axis of the current selection frame has been selected.
    * 
    * @return the selection state of the x-axis.
    */
   public boolean isXSelected()
   {
      return xSelected;
   }

   /**
    * Whether the y-axis of the current selection frame has been selected.
    * 
    * @return the selection state of the y-axis.
    */
   public boolean isYSelected()
   {
      return ySelected;
   }

   /**
    * Whether the z-axis of the current selection frame has been selected.
    * 
    * @return the selection state of the z-axis.
    */
   public boolean isZSelected()
   {
      return zSelected;
   }

   /**
    * The reference frame to which the axis selection is referring to.
    * <p>
    * This selection frame can be {@code null}.
    * </p>
    * 
    * @return the current selection frame.
    */
   public ReferenceFrame getSelectionFrame()
   {
      return selectionFrame;
   }
}

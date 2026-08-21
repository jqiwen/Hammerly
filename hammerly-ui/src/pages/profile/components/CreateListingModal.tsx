
import {  useState, useRef, ChangeEvent, DragEvent } from 'react';

import { Listing } from '@/mocks/myListing';
import { auctionApi } from '@/api/auctions';
import { useAuthStore } from '@/store/useAuthStore';

interface CreateListingModalProps {
  onClose: () => void;
  onSuccess?: () => void | Promise<void>;
  editingListing?: Listing | null;
}

export default function CreateListingModal({ onClose, onSuccess, editingListing }: CreateListingModalProps) {
  const { user } = useAuthStore();
  const isEditing = !!editingListing;
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isSavingDraft = false;
  const [isDragging, setIsDragging] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);


  const [formData, setFormData] = useState({
    title: editingListing?.title || '',
    category: editingListing?.category || '',
    description: editingListing?.description || '',
    condition: editingListing?.condition || '',
    startingPrice: editingListing?.startingPrice?.toString() || '',
    reservePrice: editingListing?.reservePrice || '',
    duration: editingListing?.duration || '7',
    images: [] as string[],
    shippingOption: editingListing?.shippingOption || 'seller',
    shippingCost: editingListing?.shippingCost || '',
  });

  const categories = [
    "Category 1",
    "Category 2",
    "Category 3",
    "Category 4",
    "Category 5",
    "Category 6",
    "Category 7",
    "Others"
  ];

  const conditions = [
    'Mint / Like New',
    'Excellent',
    'Very Good',
    'Good',
    'Fair',
    'For Parts / Restoration'
  ];

  const handleFileSelect = (files: FileList | null) => {
    if (!files) return;
    
    const remainingSlots = 8 - formData.images.length;
    const filesToProcess = Array.from(files).slice(0, remainingSlots);
    
    filesToProcess.forEach((file) => {
      if (file.type.startsWith('image/') && file.size <= 10 * 1024 * 1024) {
        const reader = new FileReader();
        reader.onload = (e) => {
          const result = e.target?.result as string;
          setFormData((prev) => ({
            ...prev,
            images: [...prev.images, result].slice(0, 8),
          }));
        };
        reader.readAsDataURL(file);
      }
    });
  };


  const handleInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    handleFileSelect(e.target.files);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileSelect(e.dataTransfer.files);
  };

  const handleRemoveImage = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      images: prev.images.filter((_, i) => i !== index),
    }));
  };

  const openFileDialog = () => {
    fileInputRef.current?.click();
  };

  const handleSubmit = async () => {
    const durationDays = Number(formData.duration);

    if (!formData.title.trim() || !formData.category.trim()) {
      setSubmitError('Title and category are required.');
      return;
    }

    if (Number.isNaN(Number(formData.startingPrice)) || Number(formData.startingPrice) <= 0) {
      setSubmitError('Starting price must be a positive number.');
      return;
    }

    if (Number.isNaN(durationDays) || durationDays <= 0) {
      setSubmitError('Duration must be a valid number of days.');
      return;
    }

    setSubmitError('');
    setIsSubmitting(true);

    try {
      await auctionApi.createAuction({
        title: formData.title.trim(),
        category: formData.category.trim(),
        description: formData.description.trim() || undefined,
        sellerId: user?.id,
        startingPrice: formData.startingPrice,
        reservePrice: formData.reservePrice || undefined,
        duration: formData.duration,
        image: formData.images[0] || undefined,
        shippingOption: formData.shippingOption,
        shippingCost: formData.shippingCost || undefined,
        condition: formData.condition || undefined,
      });

      await onSuccess?.();

      setIsSubmitting(false);
      onClose();
    } catch (error) {
      setIsSubmitting(false);
      setSubmitError(error instanceof Error ? error.message : 'Failed to publish listing.');
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl max-w-2xl w-full my-8">
        {/* Modal Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-100">
          <div>
            <h2 className="text-xl font-serif font-bold text-gray-900">
              {isEditing ? 'Edit Listing' : 'Create New Listing'}
            </h2>
            <p className="text-sm text-gray-500 mt-1">Step {step} of 3</p>
          </div>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-gray-100 transition-all cursor-pointer"
          >
            <i className="ri-close-line text-xl text-gray-500 w-5 h-5 flex items-center justify-center"></i>
          </button>
        </div>

        {/* Progress Bar */}
        <div className="px-6 pt-4">
          <div className="flex gap-2">
            {[1, 2, 3].map((s) => (
              <div
                key={s}
                className={`h-1 flex-1 rounded-full transition-all ${
                  s <= step ? 'bg-[#8B2635]' : 'bg-gray-200'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Modal Content */}
        <div className="p-6">
          {step === 1 && (
            <div className="space-y-5">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Item Details</h3>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Item Title *</label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  placeholder="e.g., Antique Victorian Gold Pocket Watch"
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Category *</label>
                <select
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm cursor-pointer"
                >
                  <option value="">Select a category</option>
                  {categories.map((cat) => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Condition *</label>
                <select
                  value={formData.condition}
                  onChange={(e) => setFormData({ ...formData, condition: e.target.value })}
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm cursor-pointer"
                >
                  <option value="">Select condition</option>
                  {conditions.map((cond) => (
                    <option key={cond} value={cond}>{cond}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Description *</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value.slice(0, 500) })}
                  placeholder="Describe your item in detail. Include history, provenance, dimensions, and any notable features..."
                  rows={4}
                  maxLength={500}
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm resize-none"
                />
                <p className="text-xs text-gray-400 mt-1">{formData.description.length}/500 characters</p>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-5">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Photos</h3>
              
              {/* Hidden file input */}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/jpg,image/webp"
                multiple
                onChange={handleInputChange}
                className="hidden"
              />
              
              {/* Drop zone */}
              <div
                onClick={openFileDialog}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                className={`border-2 border-dashed rounded-xl p-8 text-center transition-all cursor-pointer ${
                  isDragging
                    ? 'border-[#8B2635] bg-[#8B2635]/5'
                    : 'border-gray-200 hover:border-[#8B2635]/50'
                }`}
              >
                <div className={`w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4 transition-all ${
                  isDragging ? 'bg-[#8B2635]/10' : 'bg-gray-100'
                }`}>
                  <i className={`ri-image-add-line text-2xl w-6 h-6 flex items-center justify-center ${
                    isDragging ? 'text-[#8B2635]' : 'text-gray-400'
                  }`}></i>
                </div>
                <p className="text-gray-700 font-medium mb-1">
                  {isDragging ? 'Drop images here' : 'Drop images here or click to upload'}
                </p>
                <p className="text-sm text-gray-500">PNG, JPG up to 10MB. Add up to 8 photos.</p>
              </div>

              {/* Uploaded images grid */}
              {formData.images.length > 0 && (
                <div className="grid grid-cols-4 gap-3">
                  {formData.images.map((image, index) => (
                    <div key={index} className="relative aspect-square rounded-lg overflow-hidden bg-gray-100 group">
                      <img
                        src={image}
                        alt={`Upload ${index + 1}`}
                        className="w-full h-full object-cover object-top"
                      />
                      {index === 0 && (
                        <span className="absolute bottom-1 left-1 bg-[#8B2635] text-white text-xs px-2 py-0.5 rounded">
                          Main
                        </span>
                      )}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleRemoveImage(index);
                        }}
                        className="absolute top-1 right-1 w-6 h-6 bg-red-500 text-white rounded-full flex items-center justify-center hover:bg-red-600 transition-all cursor-pointer opacity-0 group-hover:opacity-100"
                      >
                        <i className="ri-close-line text-sm w-3 h-3 flex items-center justify-center"></i>
                      </button>
                    </div>
                  ))}
                  {formData.images.length < 8 && (
                    <div
                      onClick={openFileDialog}
                      className="aspect-square rounded-lg border-2 border-dashed border-gray-200 flex items-center justify-center hover:border-[#8B2635]/50 transition-all cursor-pointer"
                    >
                      <i className="ri-add-line text-2xl text-gray-400 w-6 h-6 flex items-center justify-center"></i>
                    </div>
                  )}
                </div>
              )}

              <p className="text-sm text-gray-500">
                <i className="ri-information-line w-4 h-4 inline-flex items-center justify-center mr-1"></i>
                First image will be used as the main listing photo
              </p>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-5">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Pricing & Shipping</h3>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Starting Price *</label>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                    <input
                      type="number"
                      value={formData.startingPrice}
                      onChange={(e) => setFormData({ ...formData, startingPrice: e.target.value })}
                      placeholder="0.00"
                      className="w-full pl-8 pr-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Reserve Price (Optional)</label>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                    <input
                      type="number"
                      value={formData.reservePrice}
                      onChange={(e) => setFormData({ ...formData, reservePrice: e.target.value })}
                      placeholder="0.00"
                      className="w-full pl-8 pr-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm"
                    />
                  </div>
                  <p className="text-xs text-gray-400 mt-1">Minimum price you&apos;ll accept</p>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Auction Duration *</label>
                <div className="grid grid-cols-4 gap-2">
                  {['3', '5', '7', '10'].map((days) => (
                    <button
                      key={days}
                      onClick={() => setFormData({ ...formData, duration: days })}
                      className={`py-3 rounded-lg text-sm font-medium transition-all cursor-pointer whitespace-nowrap ${
                        formData.duration === days
                          ? 'bg-[#8B2635] text-white'
                          : 'border border-gray-200 text-gray-700 hover:bg-gray-50'
                      }`}
                    >
                      {days} Days
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Shipping *</label>
                <div className="space-y-2">
                  <label className="flex items-center gap-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer">
                    <input
                      type="radio"
                      name="shipping"
                      value="seller"
                      checked={formData.shippingOption === 'seller'}
                      onChange={(e) => setFormData({ ...formData, shippingOption: e.target.value })}
                      className="w-4 h-4 text-[#8B2635] cursor-pointer"
                    />
                    <div className="flex-1">
                      <p className="font-medium text-gray-900">Seller pays shipping</p>
                      <p className="text-sm text-gray-500">Free shipping for buyers</p>
                    </div>
                  </label>
                  <label className="flex items-center gap-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer">
                    <input
                      type="radio"
                      name="shipping"
                      value="buyer"
                      checked={formData.shippingOption === 'buyer'}
                      onChange={(e) => setFormData({ ...formData, shippingOption: e.target.value })}
                      className="w-4 h-4 text-[#8B2635] cursor-pointer"
                    />
                    <div className="flex-1">
                      <p className="font-medium text-gray-900">Buyer pays shipping</p>
                      <p className="text-sm text-gray-500">Specify shipping cost</p>
                    </div>
                  </label>
                </div>
                {formData.shippingOption === 'buyer' && (
                  <div className="mt-3">
                    <div className="relative">
                      <span className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                      <input
                        type="number"
                        value={formData.shippingCost}
                        onChange={(e) => setFormData({ ...formData, shippingCost: e.target.value })}
                        placeholder="Shipping cost"
                        className="w-full pl-8 pr-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#8B2635]/20 focus:border-[#8B2635] text-sm"
                      />
                    </div>
                  </div>
                )}
              </div>

              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <div className="flex items-start gap-3">
                  <i className="ri-information-line text-amber-600 text-xl w-5 h-5 flex items-center justify-center mt-0.5"></i>
                  <div>
                    <p className="font-medium text-amber-800">Seller Fees</p>
                    <p className="text-sm text-amber-700 mt-1">A 10% seller fee will be charged on the final sale price. This fee is only charged if your item sells.</p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="flex items-center justify-between p-6 border-t border-gray-100">
          {step > 1 ? (
            <button
              onClick={() => setStep(step - 1)}
              className="flex items-center gap-2 text-gray-600 font-medium hover:text-gray-900 transition-all cursor-pointer whitespace-nowrap"
            >
              <i className="ri-arrow-left-line w-5 h-5 flex items-center justify-center"></i>
              Back
            </button>
          ) : (
            <div />
          )}
          
          {step < 3 ? (
            <button
              onClick={() => setStep(step + 1)}
              className="flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
            >
              Continue
              <i className="ri-arrow-right-line w-5 h-5 flex items-center justify-center"></i>
            </button>
          ) : (
            <div className="flex items-center gap-4">
              {submitError && (
                <p className="text-sm text-red-600">{submitError}</p>
              )}
              <button
                onClick={handleSubmit}
                disabled={isSubmitting || isSavingDraft}
                className="flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap disabled:opacity-50"
              >
                {isSubmitting ? (
                  <>
                    <i className="ri-loader-4-line animate-spin w-5 h-5 flex items-center justify-center"></i>
                    Publishing...
                  </>
                ) : (
                  <>
                    <i className="ri-check-line w-5 h-5 flex items-center justify-center"></i>
                    Publish
                  </>
                )}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
